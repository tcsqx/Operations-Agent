package org.example.service;

import com.google.gson.Gson;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.example.constant.MilvusConstants;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 向量与混合检索服务 (Hybrid RAG Search Service)
 * 支持：
 * 1. Milvus 向量检索 + 关键词/BM25 混合重排序 (Reranking)
 * 2. 本地知识库 (aiops-docs/*.md) 自动分片索引与混合相似度检索（当 Milvus 未启用或离线时无缝工作）
 * 3. 相似度阈值过滤 (Similarity Threshold Filtering)，过滤低相关度噪声分片
 * 4. 故障分析报告自动归档沉淀 (Auto-Archiving Incident Reports)
 */
@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);
    private static final Pattern EN_TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-]{2,}");
    private static final Pattern ZH_SEQUENCE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");

    @Autowired(required = false)
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private DocumentChunkService chunkService;

    @Value("${file.upload.path:aiops-docs}")
    private String uploadPath = "aiops-docs";

    @Value("${rag.similarity-threshold:0.15}")
    private float similarityThreshold = 0.15f;

    private final List<IndexedChunk> localCorpus = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();

    @PostConstruct
    public void initLocalCorpus() {
        try {
            Set<String> scannedDirs = new LinkedHashSet<>();
            List<File> candidateDirs = new ArrayList<>(Arrays.asList(
                    new File("aiops-docs"),
                    Paths.get(uploadPath).normalize().toFile(),
                    new File("Operations-Agent-main/aiops-docs"),
                    new File("Operations-Agent-main/uploads")
            ));

            // 自动通过编译类所在路径反推模块根目录（兼容 IDEA Working Directory 设为父级目录的情况）
            try {
                java.net.URL codeLocation = VectorSearchService.class.getProtectionDomain().getCodeSource().getLocation();
                if (codeLocation != null) {
                    File classesDir = Paths.get(codeLocation.toURI()).toFile();
                    // target/classes -> target -> moduleRoot
                    if (classesDir.getParentFile() != null && classesDir.getParentFile().getParentFile() != null) {
                        File moduleRoot = classesDir.getParentFile().getParentFile();
                        candidateDirs.add(new File(moduleRoot, "aiops-docs"));
                        candidateDirs.add(new File(moduleRoot, "uploads"));
                    }
                }
            } catch (Exception ignored) {
            }

            for (File dir : candidateDirs) {
                if (dir.exists() && dir.isDirectory()) {
                    String canonical = dir.getCanonicalPath();
                    if (!scannedDirs.add(canonical)) {
                        continue;
                    }
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".md") || name.endsWith(".txt"));
                    if (files != null) {
                        Arrays.sort(files, Comparator.comparing(File::getName));
                        for (File file : files) {
                            try {
                                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                                List<DocumentChunk> chunks = chunkService.chunkDocument(content, file.getPath());
                                upsertLocalChunks(file.getPath(), chunks);
                            } catch (Exception ex) {
                                logger.warn("加载本地知识库文件失败: {} - {}", file.getName(), ex.getMessage());
                            }
                        }
                    }
                }
            }
            logger.info("✅ 本地 RAG 知识库初始化完成，共加载 {} 个语义分片 (扫描目录: {})",
                    localCorpus.size(), scannedDirs);
        } catch (Exception e) {
            logger.warn("初始化本地 RAG 知识库异常: {}", e.getMessage());
        }
    }

    /**
     * 更新或插入本地内存知识库分片
     */
    public void upsertLocalChunks(String filePath, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String normalizedPath = Paths.get(filePath).normalize().toString().replace(File.separator, "/");
        Path fileNamePath = Paths.get(filePath).getFileName();
        String fileName = fileNamePath != null ? fileNamePath.toString() : normalizedPath;

        localCorpus.removeIf(c -> normalizedPath.equals(c.source)
                || c.source.endsWith("/" + fileName)
                || c.source.equals(fileName));

        int total = chunks.size();
        String docTitle = fileName;
        if (!chunks.isEmpty() && chunks.get(0).getTitle() != null && !chunks.get(0).getTitle().isEmpty()) {
            docTitle = chunks.get(0).getTitle();
        }
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("_source", normalizedPath);
            meta.put("_file_name", fileName);
            meta.put("chunkIndex", chunk.getChunkIndex());
            meta.put("totalChunks", total);
            if (chunk.getTitle() != null && !chunk.getTitle().isEmpty()) {
                meta.put("title", chunk.getTitle());
            }
            String combinedTitle = docTitle + " " + (chunk.getTitle() != null ? chunk.getTitle() : "") + " " + fileName;
            String id = UUID.nameUUIDFromBytes((normalizedPath + "_" + chunk.getChunkIndex())
                    .getBytes(StandardCharsets.UTF_8)).toString();
            localCorpus.add(new IndexedChunk(
                    id,
                    chunk.getContent(),
                    combinedTitle.trim(),
                    normalizedPath,
                    gson.toJson(meta)
            ));
        }
    }

    /**
     * 将已完成的告警/故障诊断报告自动归档沉淀至知识库
     */
    public int archiveIncidentReport(String taskId, String title, String reportContent) {
        if (reportContent == null || reportContent.trim().isEmpty()) {
            return 0;
        }
        String virtualPath = "incident-archive/" + (taskId != null ? taskId : UUID.randomUUID()) + ".md";
        String fullDoc = "# 历史故障归档: " + (title != null ? title : taskId) + "\n\n" + reportContent;
        List<DocumentChunk> chunks = chunkService.chunkDocument(fullDoc, virtualPath);
        upsertLocalChunks(virtualPath, chunks);
        logger.info("✅ 故障诊断报告已归档沉淀至 RAG 知识库: {}, 分片数: {}", virtualPath, chunks.size());
        return chunks.size();
    }

    /**
     * 搜索相似文档（支持 Milvus + 本地混合检索 + 关键词重排序 + 阈值过滤）
     *
     * @param query 查询文本
     * @param topK  返回最相似的 K 个结果
     * @return 搜索结果列表
     */
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        if (query == null || query.trim().isEmpty() || topK <= 0) {
            return Collections.emptyList();
        }

        logger.info("开始混合检索知识库文档, 查询: '{}', topK: {}", query, topK);

        // 1. 若 Milvus 客户端与 Embedding 服务均已就绪，优先尝试从 Milvus 召回候选集并进行混合重排序
        if (milvusClient != null && embeddingService != null && embeddingService.isAvailable()) {
            try {
                List<Float> queryVector = embeddingService.generateQueryVector(query);
                int recallK = Math.max(topK * 2, 6);
                SearchParam searchParam = SearchParam.newBuilder()
                        .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                        .withVectorFieldName("vector")
                        .withVectors(Collections.singletonList(queryVector))
                        .withTopK(recallK)
                        .withMetricType(io.milvus.param.MetricType.L2)
                        .withOutFields(List.of("id", "content", "metadata"))
                        .withParams("{\"nprobe\":10}")
                        .build();

                R<SearchResults> searchResponse = milvusClient.search(searchParam);
                if (searchResponse != null && searchResponse.getStatus() == 0 && searchResponse.getData() != null) {
                    SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
                    List<SearchResult> milvusResults = new ArrayList<>();
                    for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                        SearchResult result = new SearchResult();
                        result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                        String content = (String) wrapper.getFieldData("content", 0).get(i);
                        result.setContent(content);
                        float l2Distance = wrapper.getIDScore(0).get(i).getScore();
                        float vecSim = 1.0f / (1.0f + Math.max(0.0f, l2Distance));
                        Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                        String metaStr = metadataObj != null ? metadataObj.toString() : "{}";
                        result.setMetadata(metaStr);

                        float lexicalSim = computeHybridLexicalScore(query, metaStr, content);
                        float combinedScore = 0.6f * vecSim + 0.4f * lexicalSim;
                        result.setScore(combinedScore);
                        if (combinedScore >= similarityThreshold) {
                            milvusResults.add(result);
                        }
                    }
                    if (!milvusResults.isEmpty()) {
                        milvusResults.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
                        List<SearchResult> finalResults = milvusResults.subList(0, Math.min(topK, milvusResults.size()));
                        logger.info("Milvus 混合检索完成, 返回 {} 个高相关度分片", finalResults.size());
                        return finalResults;
                    }
                }
            } catch (Exception e) {
                logger.warn("Milvus 检索不可用或失败 ({}), 自动切换至本地混合检索引擎", e.getMessage());
            }
        }

        // 2. 本地知识库混合检索（BM25 词频特征 + 标题加权 + 精确术语匹配 + 阈值过滤）
        if (localCorpus.isEmpty()) {
            initLocalCorpus();
        }

        List<SearchResult> candidates = new ArrayList<>();
        for (IndexedChunk chunk : localCorpus) {
            float score = computeHybridLexicalScore(query, chunk.title + " " + chunk.source, chunk.content);
            if (score >= similarityThreshold) {
                SearchResult sr = new SearchResult();
                sr.setId(chunk.id);
                sr.setContent(chunk.content);
                sr.setScore(Math.round(score * 1000.0f) / 1000.0f);
                sr.setMetadata(chunk.metadataJson);
                candidates.add(sr);
            }
        }

        candidates.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        List<SearchResult> topResults = candidates.subList(0, Math.min(topK, candidates.size()));
        logger.info("本地混合检索完成, 候选数: {}, 阈值过滤后返回: {}", candidates.size(), topResults.size());
        return new ArrayList<>(topResults);
    }

    /**
     * 计算查询与文档分片之间的混合相关性得分 [0.0, 1.0]
     * 综合英文/标识符 Token、中文二元分词 (2-gram)、标题命中加权以及核心告警术语精确匹配
     */
    float computeHybridLexicalScore(String query, String titleAndMeta, String content) {
        if (query == null || content == null) {
            return 0.0f;
        }
        String normQuery = query.toLowerCase(Locale.ROOT).trim();
        String normTitle = (titleAndMeta != null ? titleAndMeta : "").toLowerCase(Locale.ROOT);
        String normContent = content.toLowerCase(Locale.ROOT);

        Set<String> queryTokens = extractTokens(query);
        if (queryTokens.isEmpty()) {
            return 0.0f;
        }

        int contentHits = 0;
        int titleHits = 0;
        float tfBoost = 0.0f;

        for (String token : queryTokens) {
            if (normTitle.contains(token)) {
                titleHits++;
            }
            int idx = normContent.indexOf(token);
            if (idx >= 0) {
                contentHits++;
                // 计算词频饱和度 (类似 BM25 TF 项)
                int count = 0;
                int pos = 0;
                while ((pos = normContent.indexOf(token, pos)) >= 0 && count < 10) {
                    count++;
                    pos += token.length();
                }
                tfBoost += (float) (count / (count + 1.5));
            }
        }

        float tokenCoverage = (float) contentHits / queryTokens.size();
        float titleCoverage = (float) titleHits / queryTokens.size();
        float avgTf = queryTokens.isEmpty() ? 0.0f : (tfBoost / queryTokens.size());

        // 核心运维告警/故障术语精确匹配加权
        float exactDomainBoost = 0.0f;
        String[] highValueTerms = {
                "highcpuusage", "highmemoryusage", "highdiskusage", "serviceunavailable", "slowresponse",
                "oom", "outofmemory", "oomkilled", "full gc", "connectionpoolexhausted", "redis",
                "payment-service", "order-service", "user-service", "cpu", "内存", "内存泄漏", "磁盘", "慢查询", "数据库", "连接池", "死锁", "响应时间"
        };
        for (String term : highValueTerms) {
            if (normQuery.contains(term) && (normContent.contains(term) || normTitle.contains(term))) {
                exactDomainBoost += 0.20f;
            }
        }

        float rawScore = 0.45f * tokenCoverage + 0.25f * titleCoverage + 0.15f * avgTf + Math.min(0.40f, exactDomainBoost);
        return Math.min(1.0f, rawScore);
    }

    private Set<String> extractTokens(String rawText) {
        Set<String> tokens = new LinkedHashSet<>();
        // 将驼峰如 SlowResponse / HighMemoryUsage 拆分为子词并保留原词
        String expandedEn = rawText.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        Matcher enMatcher = EN_TOKEN_PATTERN.matcher(expandedEn);
        while (enMatcher.find()) {
            String tok = enMatcher.group();
            if (!isStopWord(tok)) {
                tokens.add(tok);
                if (tok.contains("_") || tok.contains("-")) {
                    for (String sub : tok.split("[_\\-]")) {
                        if (sub.length() >= 2 && !isStopWord(sub)) {
                            tokens.add(sub);
                        }
                    }
                }
            }
        }
        // 同时保留原始连写形式（如 slowresponse, highmemoryusage）
        Matcher rawEnMatcher = EN_TOKEN_PATTERN.matcher(rawText.toLowerCase(Locale.ROOT));
        while (rawEnMatcher.find()) {
            String tok = rawEnMatcher.group();
            if (!isStopWord(tok)) {
                tokens.add(tok);
            }
        }

        Matcher zhMatcher = ZH_SEQUENCE_PATTERN.matcher(rawText);
        while (zhMatcher.find()) {
            String seq = zhMatcher.group();
            // 仅去除纯语气助词/疑问代词，保留所有实词字符（绝不能误删“查询”、“排查”等核心词）
            String cleaned = seq
                    .replace("怎么", "")
                    .replace("如何", "")
                    .replace("什么", "")
                    .replace("为什么", "")
                    .replace("请问", "")
                    .replace("一下", "")
                    .replaceAll("[的了是在有和与及或等吗呢吧啊呀]", "");
            if (cleaned.length() == 1) {
                tokens.add(cleaned);
            } else if (cleaned.length() >= 2) {
                for (int i = 0; i < cleaned.length() - 1; i++) {
                    tokens.add(cleaned.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private boolean isStopWord(String token) {
        return Set.of("the", "and", "for", "with", "from", "that", "this", "what", "how", "why", "are", "was").contains(token);
    }

    public int getLocalCorpusSize() {
        return localCorpus.size();
    }

    private static class IndexedChunk {
        final String id;
        final String content;
        final String title;
        final String source;
        final String metadataJson;

        IndexedChunk(String id, String content, String title, String source, String metadataJson) {
            this.id = id;
            this.content = content;
            this.title = title;
            this.source = source;
            this.metadataJson = metadataJson;
        }
    }

    /**
     * 搜索结果类
     */
    @Setter
    @Getter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;
    }
}

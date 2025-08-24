package com.github.istin.dmtools.context;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.agent.SummaryContextAgent;
import com.github.istin.dmtools.common.model.ToText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class ContextOrchestrator {

    private static final Logger logger = LogManager.getLogger(ContextOrchestrator.class);
    
    private Map<String, Map<String, Object>> contextMemory = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private static final int THREAD_POOL_SIZE = 10; // Increased to handle more concurrent URI processing


    protected SummaryContextAgent summaryContextAgent;
    protected ContentMergeAgent contentMergeAgent;

    @Inject
    public ContextOrchestrator(SummaryContextAgent summaryContextAgent, ContentMergeAgent contentMergeAgent) {
        this.summaryContextAgent = summaryContextAgent;
        this.contentMergeAgent = contentMergeAgent;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactory() {
            private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = defaultFactory.newThread(r);
                thread.setDaemon(true); // Make threads daemon to allow JVM to exit
                return thread;
            }
        });
    }

    public boolean processFullContent(String resourceUriKey, Object object, UriToObject uriProcessor, List<? extends UriToObject> uriToObjectList, int depth) throws Exception {
        long fullContentStart = System.currentTimeMillis();
        logger.info("TIMING: Starting ContextOrchestrator.processFullContent() for {} at {} with depth {}", resourceUriKey, fullContentStart, depth);
        
        String processorKey = uriProcessor.getClass().getSimpleName();
        contextMemory.putIfAbsent(processorKey, new LinkedHashMap<>());
        Map<String, Object> processorMemory = contextMemory.get(processorKey);

        if (resourceUriKey != null && processorMemory.containsKey(resourceUriKey) && depth == 0) {
            logger.info("TIMING: processFullContent() skipped (already cached) for {}", resourceUriKey);
            return false;
        }

        if (resourceUriKey != null) {
            if (object instanceof ToText) {
                long toTextStart = System.currentTimeMillis();
                String textContent = ((ToText) object).toText();
                long toTextDuration = System.currentTimeMillis() - toTextStart;
                logger.info("TIMING: ToText.toText() took {}ms for {} (text length: {})", toTextDuration, resourceUriKey, textContent.length());
                processorMemory.put(resourceUriKey, textContent);
            } else if (object instanceof File file) {
                long fileTransformStart = System.currentTimeMillis();
                List<FileToTextTransformer.TransformationResult> transformationResults = FileToTextTransformer.transform(file);
                long fileTransformDuration = System.currentTimeMillis() - fileTransformStart;
                logger.info("TIMING: FileToTextTransformer.transform() took {}ms for {}", fileTransformDuration, resourceUriKey);
                if (transformationResults != null) {
                    if (transformationResults.size() == 1) {
                        FileToTextTransformer.TransformationResult first = transformationResults.getFirst();
                        processorMemory.put(resourceUriKey, first.text());
                        List<File> files = first.files();
                        if (files != null) {
                            for (int j = 0; j < files.size(); j++) {
                                processorMemory.put(resourceUriKey + "_file_" + j, files.get(j));
                            }
                        }
                    } else {
                        for (int i = 0; i < transformationResults.size(); i++) {
                            FileToTextTransformer.TransformationResult transformationResult = transformationResults.get(i);
                            String partKey = resourceUriKey + "_part" + i;
                            String text = transformationResult.text();
                            if (!text.isEmpty()) {
                                processorMemory.put(partKey + "_text", text);
                            }
                            List<File> files = transformationResult.files();
                            if (files != null) {
                                for (int j = 0; j < files.size(); j++) {
                                    processorMemory.put(partKey + "_file_" + j, files.get(j));
                                }
                            }
                        }
                    }
                } else {
                    processorMemory.put(resourceUriKey, object);
                }
            } else {
                processorMemory.put(resourceUriKey, object);
            }
        }

        if (depth != 0 && !(object instanceof File) && uriToObjectList != null) {
            long processUriStart = System.currentTimeMillis();
            logger.info("TIMING: Starting recursive processUrisInContent() for {} at {}", resourceUriKey, processUriStart);
            processUrisInContent(object, uriToObjectList, depth);
            long processUriDuration = System.currentTimeMillis() - processUriStart;
            logger.info("TIMING: Recursive processUrisInContent() took {}ms for {}", processUriDuration, resourceUriKey);
        }
        
        long fullContentDuration = System.currentTimeMillis() - fullContentStart;
        logger.info("TIMING: ContextOrchestrator.processFullContent() completed in {}ms for {}", fullContentDuration, resourceUriKey);
        return false;
    }

    public void processUrisInContent(Object object, List<? extends UriToObject> uriToObjectList, int depth) throws Exception {
        long processUrisStart = System.currentTimeMillis();
        logger.info("TIMING: Starting ContextOrchestrator.processUrisInContent() at {} with depth {}", processUrisStart, depth);
        
        for (UriToObject processor : uriToObjectList) {
            long processorStart = System.currentTimeMillis();
            String processorName = processor.getClass().getSimpleName();
            logger.info("TIMING: Starting URI parsing with processor {} at {}", processorName, processorStart);
            
            Set<String> uris = processor.parseUris(String.valueOf(object));
            long parseUrisDuration = System.currentTimeMillis() - processorStart;
            logger.info("TIMING: URI parsing with {} took {}ms and found {} URIs: {}", processorName, parseUrisDuration, uris.size(), uris);
            
            if (!uris.isEmpty()) {
                long concurrentProcessStart = System.currentTimeMillis();
                // Use CompletionService for better concurrency - process futures as they complete
                CompletionService<ObjectUriPair> completionService = new ExecutorCompletionService<>(executorService);
                int submittedTasks = 0;

                // Submit all URI processing tasks
                for (String uri : uris) {
                    logger.info("TIMING: Submitting URI processing task for {}", uri);
                    completionService.submit(() -> {
                        long uriProcessStart = System.currentTimeMillis();
                        try {
                            logger.info("TIMING: Starting uriToObject() for {} at {}", uri, uriProcessStart);
                            Object resolvedObj = processor.uriToObject(uri);
                            long uriProcessDuration = System.currentTimeMillis() - uriProcessStart;
                            logger.info("TIMING: uriToObject() for {} took {}ms", uri, uriProcessDuration);
                            return new ObjectUriPair(uri, resolvedObj);
                        } catch (Exception e) {
                            long uriProcessDuration = System.currentTimeMillis() - uriProcessStart;
                            logger.info("TIMING: uriToObject() for {} failed after {}ms: {}", uri, uriProcessDuration, e.getMessage());
                            e.printStackTrace();
                            return new ObjectUriPair(uri, null);
                        }
                    });
                    submittedTasks++;
                }

                // Process results as they complete (not sequentially)
                for (int i = 0; i < submittedTasks; i++) {
                    try {
                        long waitStart = System.currentTimeMillis();
                        Future<ObjectUriPair> completedFuture = completionService.take(); // Blocks until one completes
                        long waitDuration = System.currentTimeMillis() - waitStart;
                        logger.info("TIMING: Waited {}ms for URI processing task to complete", waitDuration);
                        
                        ObjectUriPair pair = completedFuture.get(); // This should return immediately
                        if (pair.object != null) {
                            long recursiveStart = System.currentTimeMillis();
                            logger.info("TIMING: Starting recursive processFullContent() for {} at {}", pair.uri, recursiveStart);
                            processFullContent(pair.uri, pair.object, processor, uriToObjectList, depth - 1);
                            long recursiveDuration = System.currentTimeMillis() - recursiveStart;
                            logger.info("TIMING: Recursive processFullContent() for {} took {}ms", pair.uri, recursiveDuration);
                        }
                    } catch (Exception e) {
                        logger.info("TIMING: Exception while processing URI results: {}", e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                long concurrentProcessDuration = System.currentTimeMillis() - concurrentProcessStart;
                logger.info("TIMING: Concurrent URI processing with {} took {}ms for {} URIs", processorName, concurrentProcessDuration, uris.size());
            }
            
            long processorDuration = System.currentTimeMillis() - processorStart;
            logger.info("TIMING: Complete processing with {} took {}ms", processorName, processorDuration);
        }
        
        long processUrisDuration = System.currentTimeMillis() - processUrisStart;
        logger.info("TIMING: ContextOrchestrator.processUrisInContent() completed in {}ms", processUrisDuration);
    }

    // Helper class to keep uri and object together
    private static class ObjectUriPair {
        final String uri;
        final Object object;

        ObjectUriPair(String uri, Object object) {
            this.uri = uri;
            this.object = object;
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public List<ChunkPreparation.Chunk> summarize() throws Exception {
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Set<String> keys = contextMemory.keySet();
        Set<Map.Entry<String, Object>> entries = new HashSet<>();
        for (String key : keys) {
            Map<String, Object> stringObjectMap = contextMemory.get(key);
            entries.addAll(stringObjectMap.entrySet());
        }
        return chunkPreparation.prepareChunks(entries);
    }

    public void clear() {
        contextMemory.clear();
    }

}

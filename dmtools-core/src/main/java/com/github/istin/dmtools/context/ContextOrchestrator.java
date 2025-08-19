package com.github.istin.dmtools.context;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.agent.SummaryContextAgent;
import com.github.istin.dmtools.common.model.ToText;

import javax.inject.Inject;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class ContextOrchestrator {

    private Map<String, Map<String, Object>> contextMemory = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private static final int THREAD_POOL_SIZE = 5;
    private static final int TASK_TIMEOUT_SECONDS = 30;

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
        String processorKey = uriProcessor.getClass().getSimpleName();
        contextMemory.putIfAbsent(processorKey, new LinkedHashMap<>());
        Map<String, Object> processorMemory = contextMemory.get(processorKey);

        if (resourceUriKey != null && processorMemory.containsKey(resourceUriKey) && depth == 0) {
            return false;
        }

        if (resourceUriKey != null) {
            if (object instanceof ToText) {
                processorMemory.put(resourceUriKey, ((ToText) object).toText());
            } else if (object instanceof File file) {
                List<FileToTextTransformer.TransformationResult> transformationResults = FileToTextTransformer.transform(file);
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
            processUrisInContent(object, uriToObjectList, depth);
        }
        return false;
    }

    public void processUrisInContent(Object object, List<? extends UriToObject> uriToObjectList, int depth) throws Exception {
        for (UriToObject processor : uriToObjectList) {
            Set<String> uris = processor.parseUris(String.valueOf(object));
            if (!uris.isEmpty()) {
                // Create futures for parallel URI object resolution
                List<Future<ObjectUriPair>> futures = new ArrayList<>();

                for (String uri : uris) {
                    futures.add(executorService.submit(() -> {
                        try {
                            Object resolvedObj = processor.uriToObject(uri);
                            return new ObjectUriPair(uri, resolvedObj);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return new ObjectUriPair(uri, null);
                        }
                    }));
                }

                // Process results sequentially
                for (Future<ObjectUriPair> future : futures) {
                    try {
                        ObjectUriPair pair = future.get(60, TimeUnit.SECONDS); // Increased timeout for external URIs
                        if (pair.object != null) {
                            processFullContent(pair.uri, pair.object, processor, uriToObjectList, depth - 1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
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

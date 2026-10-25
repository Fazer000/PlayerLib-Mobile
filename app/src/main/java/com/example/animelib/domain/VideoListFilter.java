package com.example.animelib.domain;

import com.example.animelib.models.VideoFolder;
import com.example.animelib.models.VideoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Класс предметной логики для группировки по папкам, фильтрации и сортировки видеофайлов.
 */
public class VideoListFilter {

    public enum SortOrder {
        DATE_DESC,
        DATE_ASC,
        NAME_ASC,
        NAME_DESC,
        SIZE_DESC,
        SIZE_ASC
    }

    /**
     * Создает список папок со счетчиками файлов, включая корневую категорию "Все".
     */
    public List<VideoFolder> buildFolders(List<VideoItem> videos) {
        return buildFolders(videos, false);
    }

    public List<VideoFolder> buildFolders(List<VideoItem> videos, boolean isShortsTab) {
        Map<String, Integer> folderCounts = new HashMap<>();
        int tabTotalCount = 0;
        if (videos != null) {
            for (VideoItem v : videos) {
                boolean matchesTab = isShortsTab ? v.isShortVideo() : !v.isShortVideo();
                if (matchesTab) {
                    tabTotalCount++;
                    String folder = v.getFolderName();
                    folderCounts.put(folder, folderCounts.getOrDefault(folder, 0) + 1);
                }
            }
        }

        List<VideoFolder> foldersList = new ArrayList<>();
        foldersList.add(new VideoFolder("Все", tabTotalCount));

        List<String> sortedNames = new ArrayList<>(folderCounts.keySet());
        Collections.sort(sortedNames);
        for (String name : sortedNames) {
            foldersList.add(new VideoFolder(name, folderCounts.get(name)));
        }

        return foldersList;
    }

    /**
     * Выполняет фильтрацию по выбранной папке, поисковому запросу и сортировку по заданному критерию.
     */
    public List<VideoItem> filterAndSort(List<VideoItem> allVideos,
                                         String selectedFolder,
                                         String searchQuery,
                                         SortOrder sortOrder) {
        return filterAndSort(allVideos, selectedFolder, searchQuery, sortOrder, false);
    }

    public List<VideoItem> filterAndSort(List<VideoItem> allVideos,
                                         String selectedFolder,
                                         String searchQuery,
                                         SortOrder sortOrder,
                                         boolean isShortsTab) {
        List<VideoItem> filtered = new ArrayList<>();
        if (allVideos == null) return filtered;

        String query = (searchQuery != null) ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";
        String folder = (selectedFolder != null && !selectedFolder.isEmpty()) ? selectedFolder : "Все";

        for (VideoItem v : allVideos) {
            boolean matchesTab = isShortsTab ? v.isShortVideo() : !v.isShortVideo();

            boolean matchesFolder = "Все".equalsIgnoreCase(folder)
                    || folder.equalsIgnoreCase(v.getFolderName());

            boolean matchesSearch = query.isEmpty()
                    || (v.getTitle() != null && v.getTitle().toLowerCase(Locale.ROOT).contains(query));

            if (matchesTab && matchesFolder && matchesSearch) {
                filtered.add(v);
            }
        }

        sort(filtered, sortOrder != null ? sortOrder : SortOrder.DATE_DESC);
        return filtered;
    }

    /**
     * Сортирует список видео на месте.
     */
    public void sort(List<VideoItem> list, SortOrder sortOrder) {
        if (list == null || list.size() <= 1) return;

        switch (sortOrder) {
            case DATE_DESC:
                Collections.sort(list, (a, b) -> Long.compare(b.getDateModified(), a.getDateModified()));
                break;
            case DATE_ASC:
                Collections.sort(list, (a, b) -> Long.compare(a.getDateModified(), b.getDateModified()));
                break;
            case NAME_ASC:
                Collections.sort(list, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        a.getTitle() != null ? a.getTitle() : "",
                        b.getTitle() != null ? b.getTitle() : ""));
                break;
            case NAME_DESC:
                Collections.sort(list, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        b.getTitle() != null ? b.getTitle() : "",
                        a.getTitle() != null ? a.getTitle() : ""));
                break;
            case SIZE_DESC:
                Collections.sort(list, (a, b) -> Long.compare(b.getSizeBytes(), a.getSizeBytes()));
                break;
            case SIZE_ASC:
                Collections.sort(list, (a, b) -> Long.compare(a.getSizeBytes(), b.getSizeBytes()));
                break;
        }
    }
}

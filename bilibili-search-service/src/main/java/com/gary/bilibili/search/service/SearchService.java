package com.gary.bilibili.search.service;

import com.gary.bilibili.search.model.SearchPage;

import java.util.List;

public interface SearchService {

    SearchPage searchVideo(String keyword,
                           Long categoryId,
                           String sort,
                           Integer page,
                           Integer size);

    List<String> getHotSearch(Integer size);

    List<String> getSuggestions(String keyword, Integer size);
}

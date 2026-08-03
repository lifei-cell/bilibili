package com.gary.bilibili.social.service;

import com.gary.bilibili.social.dto.CollectionDTO;
import com.gary.bilibili.social.dto.CollectionFolderCreateDTO;
import com.gary.bilibili.social.dto.CollectionFolderUpdateDTO;
import com.gary.bilibili.social.model.CollectionPage;
import com.gary.bilibili.social.vo.CollectionFolderVO;
import com.gary.bilibili.social.vo.CollectionVO;

import java.util.List;

public interface CollectionService {

    CollectionVO collect(CollectionDTO request);

    CollectionVO uncollect(CollectionDTO request);

    CollectionPage getList(Long folderId, Integer page, Integer size);

    CollectionFolderVO createFolder(CollectionFolderCreateDTO request);

    void updateFolder(Long folderId, CollectionFolderUpdateDTO request);

    List<CollectionFolderVO> getFolders();
}

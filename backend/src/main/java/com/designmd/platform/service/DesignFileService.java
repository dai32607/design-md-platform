package com.designmd.platform.service;

import com.designmd.platform.entity.DesignFile;

import java.util.List;

public interface DesignFileService {

    List<DesignFile> listByBrandId(Long brandId);

    DesignFile getById(Long id);

    DesignFile findByBrandIdAndTitle(Long brandId, String title);

    DesignFile saveDesignFile(Long brandId, String title, String content);

    DesignFile updateContent(Long id, String content);
}

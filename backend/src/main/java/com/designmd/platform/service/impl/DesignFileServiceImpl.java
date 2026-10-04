package com.designmd.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.mapper.DesignFileMapper;
import com.designmd.platform.service.DesignFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DesignFileServiceImpl implements DesignFileService {

    private final DesignFileMapper designFileMapper;

    @Override
    public List<DesignFile> listByBrandId(Long brandId) {
        return designFileMapper.selectList(
                new LambdaQueryWrapper<DesignFile>()
                        .eq(DesignFile::getBrandId, brandId)
                        .orderByAsc(DesignFile::getId)
        );
    }

    @Override
    public DesignFile getById(Long id) {
        DesignFile file = designFileMapper.selectById(id);
        if (file == null) {
            throw new IllegalArgumentException("DESIGN.md 文件不存在: " + id);
        }
        return file;
    }

    @Override
    public DesignFile findByBrandIdAndTitle(Long brandId, String title) {
        return designFileMapper.selectOne(
                new LambdaQueryWrapper<DesignFile>()
                        .eq(DesignFile::getBrandId, brandId)
                        .eq(DesignFile::getTitle, title)
                        .last("LIMIT 1")
        );
    }

    @Override
    public DesignFile saveDesignFile(Long brandId, String title, String content) {
        DesignFile file = new DesignFile();
        file.setBrandId(brandId);
        file.setTitle(title);
        file.setContent(content);
        file.setSourceUrl("");
        designFileMapper.insert(file);
        return file;
    }

    @Override
    public DesignFile updateContent(Long id, String content) {
        DesignFile file = getById(id);
        file.setContent(content);
        designFileMapper.updateById(file);
        return file;
    }
}

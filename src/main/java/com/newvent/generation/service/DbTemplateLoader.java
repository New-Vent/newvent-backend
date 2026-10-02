package com.newvent.generation.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.repository.EventTemplateRepository;

/** 내부 생성용 로더. 사용자 접근 권한은 컨트롤러/서비스에서 별도로 검사한다. */
@Component
@Primary
@Transactional(readOnly = true)
public class DbTemplateLoader implements TemplateLoader {
    private final EventTemplateRepository repository;
    private final ResourceTemplateLoader resources;

    public DbTemplateLoader(EventTemplateRepository repository, ResourceTemplateLoader resources) {
        this.repository = repository;
        this.resources = resources;
    }

    @Override
    public List<Source> all() {
        // 호출자 정보 없는 내부 목록은 공통 템플릿만 반환한다.
        return resources.all();
    }

    @Override
    public Optional<Source> find(String code) {
        return repository.findByKey(code).flatMap(t -> t.isBuiltin()
                ? resources.find(code)
                : Optional.of(new Source(t.getCode(), t.getName(), t.getDescription(),
                        t.getThumbnailPath(), t.getHtmlContent(), false)));
    }
}

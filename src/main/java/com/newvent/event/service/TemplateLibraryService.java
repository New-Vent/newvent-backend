package com.newvent.event.service;

import java.util.Locale;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.admin.repository.AdminRepository;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.dto.request.*;
import com.newvent.event.dto.response.*;
import com.newvent.event.exception.*;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventTemplateRepository;
import com.newvent.generation.service.SavedTemplateHtml;
import com.newvent.generation.service.TemplateService;
import com.newvent.generation.service.VersionStore;
import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.Slot;

@Service
@Transactional(readOnly = true)
public class TemplateLibraryService {
    private final EventTemplateRepository templates;
    private final EventRepository events;
    private final AdminRepository admins;
    private final VersionStore versions;
    private final TemplateService renderer;
    private final EventService eventService;

    public TemplateLibraryService(EventTemplateRepository templates, EventRepository events,
            AdminRepository admins, VersionStore versions, TemplateService renderer, EventService eventService) {
        this.templates = templates;
        this.events = events;
        this.admins = admins;
        this.versions = versions;
        this.renderer = renderer;
        this.eventService = eventService;
    }

    public PageResponse<TemplateLibraryResponse> list(Long adminId, String keyword, Boolean builtin,
            boolean includeInactive, int page, int size) {
        String pattern = keyword == null || keyword.isBlank() ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = templates.findLibrary(adminId, pattern, builtin, includeInactive, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(TemplateLibraryResponse::from).toList(),
                page, size, result.getTotalElements());
    }

    public TemplatePreviewResponse preview(Long adminId, String code) {
        readable(adminId, code, false);
        return new TemplatePreviewResponse(code, renderer.initialHtml(code));
    }

    @Transactional
    public TemplateLibraryResponse register(Long adminId, TemplateRegisterRequest request) {
        Event event = events.findByIdAndDeletedAtIsNull(request.eventId())
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (event.getOwnerAdmin() == null || !adminId.equals(event.getOwnerAdmin().getId())) {
            throw new AccessDeniedException("본인 이벤트의 저장 버전만 템플릿으로 등록할 수 있습니다.");
        }
        String original = versions.htmlOf(request.eventId(), request.sourceVersionId())
                .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));
        validateStructure(original);
        String html = SavedTemplateHtml.clean(original);
        validateStructure(html);
        EventTemplate saved = templates.save(EventTemplate.custom("custom_" + UUID.randomUUID(),
                admins.getReferenceById(adminId), request.name(), request.description(), html));
        return TemplateLibraryResponse.from(saved);
    }

    @Transactional
    public TemplateLibraryResponse update(Long adminId, String code, TemplateMetadataRequest request) {
        EventTemplate template = manageable(adminId, code);
        template.updateMetadata(request.name(), request.description());
        return TemplateLibraryResponse.from(template);
    }

    @Transactional
    public void deactivate(Long adminId, String code) {
        manageable(adminId, code).deactivate();
    }

    @Transactional
    public TemplateUseResponse use(Long adminId, String code, TemplateUseRequest request) {
        readable(adminId, code, true);
        String html = renderer.initialHtml(code);
        validateStructure(html);
        var event = eventService.create(adminId, new EventCreateRequest(request.name(),
                request.startAt(), request.endAt(), code, request.grade()));
        var saved = versions.save(event.id(), html, null);
        return new TemplateUseResponse(event.id(), saved.versionId(), saved.versionNo());
    }

    /** 새 선택은 활성 템플릿만, 기존 이벤트의 재생성은 비활성도 허용한다. */
    public void checkSelection(Long adminId, String code, boolean requireActive) {
        readable(adminId, code, requireActive);
    }

    private EventTemplate readable(Long adminId, String code, boolean requireActive) {
        return templates.findByKey(code)
                .filter(t -> t.visibleTo(adminId))
                .filter(t -> !requireActive || t.isActive())
                .orElseThrow(() -> new EventException(EventErrorCode.TEMPLATE_NOT_FOUND));
    }

    private EventTemplate manageable(Long adminId, String code) {
        EventTemplate t = readable(adminId, code, false);
        if (t.isBuiltin()) throw new EventException(EventErrorCode.BUILTIN_TEMPLATE_IMMUTABLE);
        return t;
    }

    private static void validateStructure(String html) {
        if (html == null || html.isBlank()) invalid();
        var body = Jsoup.parseBodyFragment(html).body();
        for (Element e : body.select("[data-block]")) {
            if (!e.normalName().equals("section") || Block.find(e.attr("data-block")).isEmpty()
                    || !e.select("[data-block]").stream().allMatch(n -> n == e)) invalid();
        }
        for (Block b : Block.values()) {
            var matches = body.select(b.selector());
            if (matches.size() > 1 || (b.required() && matches.size() != 1)) invalid();
            if (!matches.isEmpty() && b.canEdit()) {
                String section = matches.first().outerHtml();
                if (BlockValidator.validateEdited(b, section, section).stream()
                        .anyMatch(BlockValidator.Failure::isBlocking)) invalid();
            }
        }
        if (body.select(Slot.PERIOD.selector()).isEmpty()) invalid();
        for (Element e : body.select("[data-slot]")) {
            if (Slot.find(e.attr("data-slot")).isEmpty()) invalid();
        }
    }

    private static void invalid() {
        throw new EventException(EventErrorCode.INVALID_TEMPLATE_STRUCTURE);
    }
}

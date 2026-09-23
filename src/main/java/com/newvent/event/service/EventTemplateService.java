package com.newvent.event.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.newvent.event.domain.EventTemplate;
import com.newvent.event.dto.TemplateResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventTemplateRepository;

@Service
public class EventTemplateService {

    private final EventTemplateRepository eventTemplateRepository;

    public EventTemplateService(EventTemplateRepository eventTemplateRepository) {
        this.eventTemplateRepository = eventTemplateRepository;
    }

    public List<TemplateResponse> findActiveTemplates() {
        return eventTemplateRepository.findAllActive().stream()
                .map(TemplateResponse::from)
                .toList();
    }

    public TemplateResponse findByKey(String templateKey) {
        return eventTemplateRepository.findByKey(templateKey)
                .filter(EventTemplate::isActive)
                .map(TemplateResponse::from)
                .orElseThrow(() -> new EventException(EventErrorCode.TEMPLATE_NOT_FOUND));
    }
}

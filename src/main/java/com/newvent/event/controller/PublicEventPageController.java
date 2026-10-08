package com.newvent.event.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.service.PublicEventService;

@RestController
public class PublicEventPageController {

    private final PublicEventService publicEventService;

    public PublicEventPageController(PublicEventService publicEventService) {
        this.publicEventService = publicEventService;
    }

    @GetMapping(value = "/e/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable Long id) {
        Event event = publicEventService.getPublicEvent(id);
        String page = publicEventService.publishedPageOf(event);
        if (page == null) {
            throw new EventNotFoundException();
        }
        return page;
    }
}

package com.example.devops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    
    private final VisitCountRepository repository;

    public HelloController(VisitCountRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public String hello() {
        VisitCount visit = repository.findById(1L).orElseGet(() -> {
            VisitCount newVisit = new VisitCount();
            newVisit.setId(1L);
            newVisit.setCount(0L);
            return newVisit;
        });

        visit.setCount(visit.getCount() + 1);
        repository.save(visit);

        return "Hello DevOps! 방문 횟수: " + visit.getCount();
    }
}
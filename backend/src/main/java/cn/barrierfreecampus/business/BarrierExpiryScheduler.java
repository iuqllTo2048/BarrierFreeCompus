package cn.barrierfreecampus.business;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BarrierExpiryScheduler {
    private final BusinessService service;

    public BarrierExpiryScheduler(BusinessService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.barrier-expiry-interval-ms:60000}")
    public void expireBarriers() {
        service.expireBarriers();
    }
}

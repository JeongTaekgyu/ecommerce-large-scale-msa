package com.example.timesaleservice.controller.v3;

import com.example.timesaleservice.dto.TimeSaleDto;
import com.example.timesaleservice.service.v3.AsyncTimeSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/time-sales")
@RequiredArgsConstructor
public class AsyncTimeSaleController {
    private final AsyncTimeSaleService asyncTimeSaleService;

    @PostMapping
    public ResponseEntity<TimeSaleDto.Response> createTimeSale(@RequestBody TimeSaleDto.CreateRequest request) {
        return ResponseEntity.ok(TimeSaleDto.Response.from(asyncTimeSaleService.createTimeSale(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TimeSaleDto.Response> getTimeSale(@PathVariable Long id) {
        return ResponseEntity.ok(TimeSaleDto.Response.from(asyncTimeSaleService.getTimeSale(id)));
    }

    @GetMapping
    public ResponseEntity<Page<TimeSaleDto.Response>> getOngoingTimeSales(Pageable pageable) {
        return ResponseEntity.ok(asyncTimeSaleService.getOngoingTimeSales(pageable).map(TimeSaleDto.Response::from));
    }

    @PostMapping("/{id}/purchase")
    public ResponseEntity<TimeSaleDto.AsyncPurchaseResponse> purchaseTimeSale(
            @PathVariable Long id,
            @RequestBody TimeSaleDto.PurchaseRequest request) {
        String requestId = asyncTimeSaleService.purchaseTimeSale(id, request);
        return ResponseEntity.ok(TimeSaleDto.AsyncPurchaseResponse.builder()
                .requestId(requestId)
                .status("PENDING")
                .build());
    }

    // purchaseTimeSale 에서는 실제 구매되었는지 결과를 반환하지 않기 때문에 해당 메서드로 결과값을 확인한다.
    @GetMapping("/purchase/result/{timeSaleId}/{requestId}")
    public ResponseEntity<TimeSaleDto.AsyncPurchaseResponse> getPurchaseResult(
            @PathVariable Long timeSaleId,
            @PathVariable String requestId) {
        return ResponseEntity.ok(asyncTimeSaleService.getPurchaseResult(timeSaleId, requestId));
    }
}

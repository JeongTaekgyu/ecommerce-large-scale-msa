package com.example.timesaleservice.service.v1;

import com.example.timesaleservice.aop.TimeSaleMetered;
import com.example.timesaleservice.domain.Product;
import com.example.timesaleservice.domain.TimeSale;
import com.example.timesaleservice.domain.TimeSaleOrder;
import com.example.timesaleservice.domain.TimeSaleStatus;
import com.example.timesaleservice.dto.TimeSaleDto;
import com.example.timesaleservice.repository.ProductRepository;
import com.example.timesaleservice.repository.TimeSaleOrderRepository;
import com.example.timesaleservice.repository.TimeSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TimeSaleService {

    private final ProductRepository productRepository;
    private final TimeSaleRepository timeSaleRepository;
    private final TimeSaleOrderRepository timeSaleOrderRepository;

    @Transactional
    public TimeSale createTimeSale(TimeSaleDto.CreateRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        validateTimeSale(request.getQuantity(), request.getDiscountPrice(),
                request.getStartAt(), request.getEndAt());

        TimeSale timeSale = TimeSale.builder()
                .product(product)
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .discountPrice(request.getDiscountPrice())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(TimeSaleStatus.ACTIVE)
                .build();

        return timeSaleRepository.save(timeSale);
    }

    @Transactional(readOnly = true)
    public TimeSale getTimeSale(Long timeSaleId) {
        return timeSaleRepository.findById(timeSaleId)
                .orElseThrow(() -> new IllegalArgumentException("Time sale not found"));
    }

    // 현재 진행 중인 타임세일 리스트를 가져옴
    @Transactional(readOnly = true)
    public Page<TimeSale> getOngoingTimeSales(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return timeSaleRepository.findAllByStartAtBeforeAndEndAtAfterAndStatus(now, TimeSaleStatus.ACTIVE, pageable);
    }

    @Transactional // 분산 시스템에서는 해당 시스템에 빈틈이 있을 수 있음
    @TimeSaleMetered(version = "v1")
    public TimeSale purchaseTimeSale(Long timeSaleId, TimeSaleDto.PurchaseRequest request) {
        TimeSale timeSale = timeSaleRepository.findByIdWithPessimisticLock(timeSaleId)
                .orElseThrow(() -> new IllegalArgumentException("TimeSale not found"));

        // timeSale 이 정상적으로 있으면 구매 로직을 실행한다.
        timeSale.purchase(request.getQuantity());
        timeSaleRepository.save(timeSale);

        TimeSaleOrder order = TimeSaleOrder.builder()
                .userId(request.getUserId())
                .timeSale(timeSale)
                .quantity(request.getQuantity())
                .discountPrice(timeSale.getDiscountPrice())
                .build();

        TimeSaleOrder savedOrder = timeSaleOrderRepository.save(order);
        savedOrder.complete(); // status를 COMPLETE로 변경

        return timeSale;
    }

    private void validateTimeSale(Long quantity, Long discountPrice, LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (discountPrice <= 0) {
            throw new IllegalArgumentException("Discount price must be positive");
        }
    }
}

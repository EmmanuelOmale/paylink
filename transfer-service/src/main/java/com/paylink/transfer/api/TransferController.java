package com.paylink.transfer.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paylink.transfer.service.TransferResult;
import com.paylink.transfer.service.TransferService;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<String> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TransferRequest request) {
        TransferResult result = transferService.transfer(idempotencyKey, request);
        return ResponseEntity.status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.json());
    }

    @GetMapping("/{rrn}")
    public TransferDetailsResponse find(@PathVariable String rrn) {
        return TransferDetailsResponse.from(transferService.find(rrn));
    }
}

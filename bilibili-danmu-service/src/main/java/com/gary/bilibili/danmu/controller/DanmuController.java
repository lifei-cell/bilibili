package com.gary.bilibili.danmu.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.danmu.dto.DanmuSendDTO;
import com.gary.bilibili.danmu.model.DanmuPage;
import com.gary.bilibili.danmu.service.DanmuService;
import com.gary.bilibili.danmu.service.DanmuWebSocketTicketService;
import com.gary.bilibili.danmu.vo.DanmuCountVO;
import com.gary.bilibili.danmu.vo.DanmuListVO;
import com.gary.bilibili.danmu.vo.DanmuSendVO;
import com.gary.bilibili.danmu.vo.DanmuWebSocketTicketVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/danmu", "/api/danmu"})
public class DanmuController {

    private final DanmuService danmuService;
    private final DanmuWebSocketTicketService webSocketTicketService;

    public DanmuController(DanmuService danmuService,
                           DanmuWebSocketTicketService webSocketTicketService) {
        this.danmuService = danmuService;
        this.webSocketTicketService = webSocketTicketService;
    }

    @GetMapping("/list/{videoId}")
    public Result<List<DanmuListVO>> getList(
            @PathVariable @Min(1) Long videoId,
            @RequestParam(required = false) @Min(0) Integer startTime,
            @RequestParam(required = false) @Min(0) Integer endTime,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "500") @Min(1) @Max(1000) Integer size) {
        DanmuPage result = danmuService.getList(
                videoId, startTime, endTime, page, size);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @PostMapping("/send")
    public Result<DanmuSendVO> send(@Valid @RequestBody DanmuSendDTO request) {
        return Result.ok(danmuService.send(request));
    }

    @GetMapping("/count/{videoId}")
    public Result<DanmuCountVO> getCount(@PathVariable @Min(1) Long videoId) {
        return Result.ok(danmuService.getCount(videoId));
    }

    @PostMapping("/ws-ticket/{videoId}")
    public Result<DanmuWebSocketTicketVO> issueWebSocketTicket(
            @PathVariable @Min(1) Long videoId) {
        DanmuWebSocketTicketService.IssuedTicket ticket = webSocketTicketService.issue(videoId);
        DanmuWebSocketTicketVO result = new DanmuWebSocketTicketVO();
        result.setTicket(ticket.value());
        result.setExpiresIn(ticket.expiresIn());
        return Result.ok(result);
    }
}

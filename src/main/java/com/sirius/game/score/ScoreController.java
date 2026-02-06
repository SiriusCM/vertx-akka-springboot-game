package com.sirius.game.score;

import com.sirius.game.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/score")
public class ScoreController {
    
    private final ConcurrentHashMap<String, AtomicInteger> scoreMap = new ConcurrentHashMap<>();
    
    @GetMapping("/get")
    public Result<Integer> getScore(@RequestParam String playerId) {
        AtomicInteger score = scoreMap.get(playerId);
        int currentScore = score != null ? score.get() : 0;
        return Result.success(currentScore);
    }
    
    @GetMapping("/add")
    public Result<Integer> addScore(@RequestParam String playerId, @RequestParam int points) {
        AtomicInteger score = scoreMap.computeIfAbsent(playerId, k -> new AtomicInteger(0));
        int newScore = score.addAndGet(points);
        log.info("Added {} points to player {}. New score: {}", points, playerId, newScore);
        return Result.success(newScore);
    }
    
    @GetMapping("/subtract")
    public Result<Integer> subtractScore(@RequestParam String playerId, @RequestParam int points) {
        AtomicInteger score = scoreMap.computeIfAbsent(playerId, k -> new AtomicInteger(0));
        int newScore = score.updateAndGet(current -> Math.max(0, current - points));
        log.info("Subtracted {} points from player {}. New score: {}", points, playerId, newScore);
        return Result.success(newScore);
    }
}
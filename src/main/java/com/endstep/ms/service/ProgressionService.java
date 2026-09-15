package com.endstep.ms.service;

import com.endstep.ms.dto.ProgressionDtos.AchievementView;
import com.endstep.ms.dto.ProgressionDtos.LeaderboardEntry;
import com.endstep.ms.dto.ProgressionDtos.UserProgressionView;
import com.endstep.ms.entity.Achievement;
import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.entity.User;
import com.endstep.ms.entity.UserAchievement;
import com.endstep.ms.entity.UserStats;
import com.endstep.ms.repository.AchievementRepository;
import com.endstep.ms.repository.UserAchievementRepository;
import com.endstep.ms.repository.UserRepository;
import com.endstep.ms.repository.UserStatsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * XP, nivel (ate 100), titulo e conquistas (mesmos objetivos pra todos os
 * jogadores). Chamado quando uma partida termina.
 *
 * @author Kauã Ferreira
 * @since 2026-09-15
 */
@Service
public class ProgressionService {

    public static final int MAX_LEVEL = 100;
    private static final int WIN_XP = 60;
    private static final int PARTICIPATION_XP = 20;

    private final UserStatsRepository stats;
    private final AchievementRepository achievementRepo;
    private final UserAchievementRepository userAchievementRepo;
    private final UserRepository users;

    public ProgressionService(UserStatsRepository stats, AchievementRepository achievementRepo,
                              UserAchievementRepository userAchievementRepo, UserRepository users) {
        this.stats = stats;
        this.achievementRepo = achievementRepo;
        this.userAchievementRepo = userAchievementRepo;
        this.users = users;
    }

    @Transactional
    public void onGameFinished(Game game, List<GamePlayer> players) {
        if (game.isXpAwarded()) {
            return;
        }
        game.setXpAwarded(true);
        Long winnerId = players.stream()
                .filter(p -> GamePlayer.Status.PLAYING.name().equals(p.getStatus()))
                .map(GamePlayer::getUserId)
                .findFirst()
                .orElse(null);
        List<Achievement> catalog = achievementRepo.findAll();
        for (GamePlayer p : players) {
            awardGame(p.getUserId(), p.getUserId().equals(winnerId), catalog);
        }
    }

    private void awardGame(Long userId, boolean won, List<Achievement> catalog) {
        UserStats us = statsOf(userId);
        us.setGamesPlayed(us.getGamesPlayed() + 1);
        if (won) {
            us.setGamesWon(us.getGamesWon() + 1);
        }
        us.setXp(us.getXp() + (won ? WIN_XP : PARTICIPATION_XP));
        us.setLevel(levelForXp(us.getXp()));
        stats.save(us);
        checkAchievements(userId, us, catalog);
    }

    private UserStats statsOf(Long userId) {
        return stats.findById(userId).orElseGet(() -> {
            UserStats n = new UserStats();
            n.setUserId(userId);
            return n;
        });
    }

    private void checkAchievements(Long userId, UserStats us, List<Achievement> catalog) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 10) {
            changed = false;
            for (Achievement a : catalog) {
                UserAchievement ua = userAchievementRepo.findByUserIdAndAchievementId(userId, a.getId())
                        .orElseGet(() -> {
                            UserAchievement n = new UserAchievement();
                            n.setUserId(userId);
                            n.setAchievementId(a.getId());
                            return n;
                        });
                if (ua.getUnlockedAt() != null) {
                    continue;
                }
                int value = metricValue(a.getMetric(), us);
                ua.setProgress(Math.min(value, a.getTarget()));
                if (value >= a.getTarget()) {
                    ua.setUnlockedAt(Instant.now());
                    us.setXp(us.getXp() + a.getXpReward());
                    int newLevel = levelForXp(us.getXp());
                    if (newLevel != us.getLevel()) {
                        us.setLevel(newLevel);
                        changed = true;
                    }
                    stats.save(us);
                }
                userAchievementRepo.save(ua);
            }
        }
    }

    private static int metricValue(String metric, UserStats us) {
        return switch (metric) {
            case "GAMES_PLAYED" -> us.getGamesPlayed();
            case "GAMES_WON" -> us.getGamesWon();
            case "LEVEL" -> us.getLevel();
            default -> 0;
        };
    }

    public static long cumulativeXpForLevel(int level) {
        long n = level - 1;
        return 100L * n * (n + 1) / 2;
    }

    public static int levelForXp(long xp) {
        int level = 1;
        while (level < MAX_LEVEL && cumulativeXpForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    public static String titleForLevel(int level) {
        if (level >= 100) {
            return "Imortal";
        } else if (level >= 90) {
            return "Mítico";
        } else if (level >= 80) {
            return "Lenda";
        } else if (level >= 70) {
            return "Grão-Mestre";
        } else if (level >= 60) {
            return "Mestre";
        } else if (level >= 50) {
            return "Elite";
        } else if (level >= 40) {
            return "Especialista";
        } else if (level >= 30) {
            return "Veterano";
        } else if (level >= 20) {
            return "Adepto";
        } else if (level >= 10) {
            return "Aprendiz";
        }
        return "Novato";
    }

    @Transactional(readOnly = true)
    public UserProgressionView view(Long userId) {
        UserStats us = statsOf(userId);
        List<Achievement> catalog = achievementRepo.findAllByOrderByPositionAsc();
        Map<Long, UserAchievement> mine = userAchievementRepo.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua));
        List<AchievementView> views = catalog.stream().map(a -> {
            UserAchievement ua = mine.get(a.getId());
            int progress = ua != null ? ua.getProgress() : 0;
            boolean unlocked = ua != null && ua.getUnlockedAt() != null;
            Instant unlockedAt = ua != null ? ua.getUnlockedAt() : null;
            return new AchievementView(a.getCode(), a.getName(), a.getDescription(), a.getMetric(),
                    a.getTarget(), progress, a.getXpReward(), unlocked, unlockedAt);
        }).toList();
        int level = us.getLevel();
        long intoLevel = us.getXp() - cumulativeXpForLevel(level);
        long forNext = level >= MAX_LEVEL ? 0 : cumulativeXpForLevel(level + 1) - cumulativeXpForLevel(level);
        double winRate = us.getGamesPlayed() == 0 ? 0.0 : (double) us.getGamesWon() / us.getGamesPlayed();
        return new UserProgressionView(us.getXp(), level, titleForLevel(level), MAX_LEVEL, intoLevel, forNext,
                us.getGamesPlayed(), us.getGamesWon(), winRate, views);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(int limit) {
        List<UserStats> top = stats.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "xp")))
                .getContent();
        Map<Long, User> byId = users.findAllById(top.stream().map(UserStats::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return top.stream().map(us -> {
            User u = byId.get(us.getUserId());
            return new LeaderboardEntry(us.getUserId(), u == null ? "?" : u.getUsername(),
                    u == null ? "?" : u.getDisplayName(), us.getXp(), us.getLevel(),
                    titleForLevel(us.getLevel()), us.getGamesWon());
        }).toList();
    }
}

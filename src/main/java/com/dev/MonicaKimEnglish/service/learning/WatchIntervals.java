package com.dev.MonicaKimEnglish.service.learning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 시간 이동과 반복 재생을 중복 산입하지 않는 밀리초 구간 합집합. */
final class WatchIntervals {
    private final List<Interval> intervals = new ArrayList<>();

    static WatchIntervals parse(String value) {
        WatchIntervals result = new WatchIntervals();
        if (value == null || value.isBlank()) return result;
        for (String item : value.split(";")) {
            String[] parts = item.split(":", -1);
            if (parts.length != 2) throw new IllegalStateException("학습 구간 데이터가 올바르지 않습니다.");
            long start = Long.parseLong(parts[0]);
            long end = Long.parseLong(parts[1]);
            if (start < 0 || end <= start) throw new IllegalStateException("학습 구간 데이터가 올바르지 않습니다.");
            result.intervals.add(new Interval(start, end));
        }
        result.merge();
        return result;
    }

    void add(double startSeconds, double endSeconds, long durationSeconds) {
        long start = Math.max(0, Math.round(startSeconds * 1000));
        long end = Math.min(durationSeconds * 1000, Math.round(endSeconds * 1000));
        if (end <= start) return;
        intervals.add(new Interval(start, end));
        merge();
    }

    long watchedMilliseconds() { return intervals.stream().mapToLong(i -> i.end - i.start).sum(); }

    String encode() {
        return intervals.stream().map(i -> i.start + ":" + i.end).collect(java.util.stream.Collectors.joining(";"));
    }

    private void merge() {
        intervals.sort(Comparator.comparingLong(i -> i.start));
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : intervals) {
            if (merged.isEmpty() || interval.start > merged.get(merged.size() - 1).end) {
                merged.add(interval);
            } else {
                Interval previous = merged.remove(merged.size() - 1);
                merged.add(new Interval(previous.start, Math.max(previous.end, interval.end)));
            }
        }
        if (merged.size() > 4096) throw new IllegalArgumentException("학습 구간이 너무 많습니다. 정상 재생으로 이어서 수강해주세요.");
        intervals.clear();
        intervals.addAll(merged);
    }

    private record Interval(long start, long end) { }
}

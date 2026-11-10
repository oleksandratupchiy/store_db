"""Validate real benchmark CSV and summarize medians across independent runs."""
import csv
from collections import defaultdict
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
import math
from pathlib import Path
import statistics
import sys

SCENARIOS = ('getOrderById', 'getLatestOrdersForCustomer',
             'getOrdersByDateRange', 'getCustomerStats')
STORES = ('postgresql', 'mongodb')
PROFILE = ('orders', 'seed', 'warmup', 'repetitions')
METRICS = ('median_ms', 'p95_ms', 'mean_ms')
LABELS = dict(zip(SCENARIOS, ('ID', 'Останні 20', 'Діапазон дат', 'Статистика')))


def load_runs(folder):
    rows = []
    for path in sorted(folder.glob('benchmark_*.csv')):
        with path.open(encoding='utf-8-sig', newline='') as stream:
            run = list(csv.DictReader(stream))
        if len(run) != 8:
            raise ValueError(f'{path}: expected 8 rows, got {len(run)}')
        pairs = [(r['scenario'], r['store']) for r in run]
        if len(set(pairs)) != 8 or set(pairs) != {(s, d) for s in SCENARIOS for d in STORES}:
            raise ValueError(f'{path}: missing/duplicate scenario or store')
        profiles = {tuple(int(r[k]) for k in PROFILE) for r in run}
        if len(profiles) != 1:
            raise ValueError(f'{path}: inconsistent dataset/parameters')
        for row in run:
            for key in PROFILE:
                row[key] = int(row[key])
            for key in METRICS:
                row[key] = float(row[key])
                if not math.isfinite(row[key]) or row[key] < 0:
                    raise ValueError(f'{path}: invalid {key}')
            if row['p95_ms'] < row['median_ms']:
                raise ValueError(f'{path}: p95 < median')
            if datetime.fromisoformat(row['timestamp_utc']).utcoffset() is None:
                raise ValueError(f'{path}: timestamp must include timezone')
            row['source_file'] = path.name
            rows.append(row)
    if not rows:
        raise ValueError('No benchmark CSV found')
    return rows


def summarize(rows):
    groups = defaultdict(list)
    for row in rows:
        key = tuple(row[k] for k in PROFILE) + (row['scenario'], row['store'])
        groups[key].append(row['median_ms'])
    result = []
    keys = sorted(groups, key=lambda k: (k[:4], SCENARIOS.index(k[4]), STORES.index(k[5])))
    for key in keys:
        values = groups[key]
        record = dict(zip(PROFILE + ('scenario', 'store'), key))
        record.update(runs=len(values), mean_median_ms=statistics.mean(values),
                      sample_sd_median_ms=statistics.stdev(values) if len(values) > 1 else None,
                      min_median_ms=min(values), max_median_ms=max(values))
        result.append(record)
    return result


def render_table(summary):
    def display(value):
        return str(Decimal(str(value)).quantize(Decimal('0.001'), rounding=ROUND_HALF_UP))
    lines = ['| Замовлень | Сценарій | БД | n | Середнє медіан, ms | s, ms | Min, ms | Max, ms |',
             '|---:|---|---|---:|---:|---:|---:|---:|']
    for r in summary:
        if r['orders'] not in (1000, 10000, 100000):
            continue
        sd = '—' if r['sample_sd_median_ms'] is None else display(r['sample_sd_median_ms'])
        store = 'PostgreSQL' if r['store'] == 'postgresql' else 'MongoDB'
        lines.append(f"| {r['orders']:,} | {LABELS[r['scenario']]} | {store} | {r['runs']} | "
                     f"{display(r['mean_median_ms'])} | {sd} | {display(r['min_median_ms'])} | {display(r['max_median_ms'])} |")
    return '\n'.join(lines)


def main():
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else 'testing/results')
    rows = load_runs(folder)
    summary = summarize(rows)
    principal = [r for r in summary if r['orders'] in (1000, 10000, 100000)]
    if {r['orders'] for r in principal} != {1000, 10000, 100000}:
        raise ValueError('Missing principal dataset')
    if any(r['runs'] < 3 for r in principal):
        raise ValueError('At least 3 independent runs per principal dataset are required')
    if any((r['seed'], r['warmup'], r['repetitions']) != (42, 50, 200) for r in summary):
        raise ValueError('This experiment requires seed=42, warmup=50, repetitions=200')
    target = folder / 'between_run_summary.csv'
    with target.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=tuple(summary[0]))
        writer.writeheader()
        for row in summary:
            writer.writerow({k: ('' if v is None else f'{v:.6f}' if isinstance(v, float) else v)
                             for k, v in row.items()})
    sources = sorted({r['source_file'] for r in rows})
    markdown = ('# Статистика медіан між запусками\n\n'
                'Seed=42; 50 прогрівів і 200 вимірювань на сценарій/БД в кожному запуску.\n'
                'n — кількість запусків. s — вибіркове стандартне відхилення медіан (n−1).\n'
                'Це не середня затримка всіх операцій і не глобальний p95.\n'
                'Smoke на 100 замовлень враховано в CSV окремо: n=1, s не визначено.\n\n'
                + render_table(summary) + '\n\n## Файли-джерела\n\n'
                + '\n'.join(f'- `{name}`' for name in sources) + '\n')
    (folder / 'between_run_summary.md').write_text(markdown, encoding='utf-8')
    print(f'Validated {len(sources)} CSV files, {len(rows)} rows; wrote {len(summary)} groups to {target}')
    print(render_table(summary))


if __name__ == '__main__':
    main()

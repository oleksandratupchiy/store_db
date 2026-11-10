"""Plot only complete, measured benchmark CSV to standalone SVG with matplotlib."""
import csv
import pathlib
import sys
import statistics
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

folder = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'testing/results')
rows = []
for path in sorted(folder.glob('benchmark_*.csv')):
    with path.open(encoding='utf-8') as stream:
        run = list(csv.DictReader(stream))
    if len(run) != 8:
        print(f'Skipping incomplete run: {path}', file=sys.stderr)
        continue
    for row in run:
        row['run'] = path.stem
        rows.append(row)
if not rows:
    raise SystemExit('No real benchmark CSV found; run benchmark first.')
plt.rcParams['svg.fonttype'] = 'none'
scenarios = sorted({r['scenario'] for r in rows})
datasets = sorted({(int(r['orders']), r['seed']) for r in rows})
run_counts = {key: len({r['run'] for r in rows if (int(r['orders']), r['seed']) == key}) for key in datasets}
for metric in ('median_ms', 'p95_ms'):
    fig, axes = plt.subplots(2, 2, figsize=(12, 8), constrained_layout=True)
    for ax, scenario in zip(axes.flat, scenarios):
        for store, offset, color in (('postgresql', -.19, '#306998'), ('mongodb', .19, '#16834a')):
            values, ranges = [], []
            for count, seed in datasets:
                match = [float(r[metric]) for r in rows if int(r['orders']) == count and r['seed'] == seed and r['scenario'] == scenario and r['store'] == store]
                if len(match) != run_counts[(count, seed)]:
                    raise SystemExit(f'Ambiguous/incomplete runs: {count}, seed={seed}')
                values.append(statistics.mean(match))
                ranges.append((min(match), max(match)) if len(match) > 1 else None)
            bars = ax.bar([i + offset for i in range(len(datasets))], values, width=.38, color=color, label=store)
            for bar, value, observed_range in zip(bars, values, ranges):
                x = bar.get_x() + bar.get_width() / 2
                if observed_range is not None:
                    low, high = observed_range
                    ax.errorbar(x, value, yerr=[[value-low], [high-value]], fmt='none', color='black', capsize=3, linewidth=1)
                ax.annotate(f'{value:.2f}', (x, observed_range[1] if observed_range else value), xytext=(0, 4),
                            textcoords='offset points', ha='center', fontsize=8)
        ax.set_title(scenario, fontsize=11)
        ax.set_xticks(range(len(datasets)), [f'{n:,}\nseed {seed}, n={run_counts[(n, seed)]}' for n, seed in datasets])
        ax.set_xlabel('orders; n = independent runs (n=1: no range whisker)')
        ax.set_ylabel('milliseconds')
        ax.set_ylim(0, ax.get_ylim()[1] * 1.15)
        ax.grid(axis='y', alpha=.2)
        ax.set_axisbelow(True)
        ax.legend(fontsize=8, loc='upper left')
    statistic = 'medians' if metric == 'median_ms' else 'p95 values'
    fig.suptitle(f'Mean of run {statistic}; whiskers = observed min-max across runs')
    target = folder / f'{metric}.svg'
    fig.savefig(target)
    target.write_text('\n'.join(line.rstrip() for line in target.read_text(encoding='utf-8').splitlines()) + '\n', encoding='utf-8')
    plt.close(fig)
    print(target)

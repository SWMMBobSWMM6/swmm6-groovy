#!/bin/bash
# run_chunk.sh NAME SET [IDS_FILE|-] [ENGINE] [--resume]
# One gate run of the Groovy port, on the 480 corpus models (SET=corpus) or the 452 extra decks (SET=extra):
#   python3 gate/gate.py NAME ENGINE [--models models_extra.tsv --ref ref_outputs_extra] [--ids IDS] -j 1 --timeout 1800 --prune
# inside flock on the shared corpus lock (waiting up to 2 hours for it), niced, with TMPDIR and the results folder in
# /dev/shm (SWMM5 lessons/22), ENGINE defaulting to this port's runswmm6_saves.sh (which backs up and restores the
# files a deck SAVEs). Afterwards the outputs of any failing model are copied to gate/results/NAME on disk (evidence)
# and the memory copy removed; gate/results/NAME.json and .jsonl stay where gate.py writes them, and a copy of the
# JSON and of the log go to groovy/results/. The start and end times go to groovy/gate/NAME_times.txt.
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../README.md.
set -u
NAME=$1; SET=$2; IDS=${3:--}; RESUME=""
K=$(cd "$(dirname "$0")/.." && pwd); ROOT=$(cd "$K/.." && pwd)
ENGINE=${4:-$K/runswmm6_saves.sh}
[ "${5:-}" = --resume ] && RESUME=--resume
LOCK=${SWMM_GATE_LOCK:-${TMPDIR:-/tmp}/swmm6-gate.lock}  # one corpus run at a time
EXTRA=""; [ "$SET" = extra ] && EXTRA="--models models_extra.tsv --ref ref_outputs_extra"
IDSARG=""; [ "$IDS" != - ] && IDSARG="--ids $(cd "$(dirname "$IDS")" && pwd)/$(basename "$IDS")"
cd "$ROOT/gate"
M=/dev/shm/gate_$NAME
mkdir -p "$M"
if [ -L "results/$NAME" ]; then rm "results/$NAME"; elif [ -d "results/$NAME" ]; then cp -a "results/$NAME"/. "$M"/ 2>/dev/null; rm -rf "results/$NAME"; fi
ln -s "$M" "results/$NAME"
echo "queued $(date -Is)" >> "$K/gate/${NAME}_times.txt"
TMPDIR=/dev/shm flock -w 6600 "$LOCK" bash -c "echo \"started \$(date -Is)\" >> '$K/gate/${NAME}_times.txt'; \
  nice -n 10 python3 gate.py '$NAME' '$ENGINE' $EXTRA $IDSARG -j 1 --timeout 1800 --prune $RESUME" >> "$K/gate/${NAME}_run.txt" 2>&1
rc=$?
echo "ended $(date -Is) rc=$rc" >> "$K/gate/${NAME}_times.txt"
rm "results/$NAME"
mkdir -p "results/$NAME"
cp -a "$M"/. "results/$NAME"/ 2>/dev/null
rmdir "results/$NAME" 2>/dev/null
rm -rf "$M"
[ -f "results/$NAME.json" ] && cp "results/$NAME.json" "$K/results/$NAME.json"
cp "$K/gate/${NAME}_run.txt" "$K/results/${NAME}_run.txt"
tail -4 "$K/gate/${NAME}_run.txt"

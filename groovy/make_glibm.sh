#!/bin/bash
# make_glibm.sh [GLIBM_BUILD]
# Regenerate src/cpp/Glibm.groovy and src/cpp/Glibm2.groovy, the Groovy translations of glibc 2.39's math routines,
# with the Groovy back end of tools/libm/gim2x.py, as tools/libm/make_libm.sh makes the Go, Java and TypeScript ones:
#   set 1 (Glibm.groovy): exp, log, pow, sin, cos, tan, asin, acos, atan (FMA variants) and log10;
#   set 2 (Glibm2.groovy): atan2, log2, sinh, tanh, expm1, log1p (FMA variants), cosh, cbrt, hypot (generic).
# GLIBM_BUILD holds the GIMPLE dumps (gimple/*.254t.optimized) and tables.json that tools/libm/build_glibm.sh and
# extract_tables.py write (they compile glibc's sources and read the objects' tables; nothing of glibc is run).
# The runtimes GlibmRt.groovy and Glibm2Rt.groovy are hand-written Groovy. Written by Claude (Anthropic) for Robert
# Dickinson; see README.md.
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/.." && pwd)
W=${1:-$ROOT/build/glibm}; G=$W/gimple
X="python3 $ROOT/tools/libm/gim2x.py"
LG="Derived from the GNU C Library (glibc) 2.39 and so, like glibc's libm, licensed under the GNU Lesser General Public License, version 2.1 or later (LGPL-2.1-or-later)."
WB="Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see groovy/README.md."
$X groovy "$HERE/src/cpp/Glibm.groovy" $G/e_exp.c.254t.optimized $G/e_log.c.254t.optimized $G/e_pow.c.254t.optimized \
    $G/s_sin.c.254t.optimized $G/s_tan.c.254t.optimized $G/e_asin.c.254t.optimized $G/s_atan.c.254t.optimized \
    $G/e_log10.c.254t.optimized --tables "$W/tables.json" --package cpp --rt GlibmRt \
    --name my_exp_fma=exp --name my_log=log --name my_pow=pow --name my_sin=sin --name my_cos=cos --name my_tan=tan \
    --name my_asin=ieee754_asin --name my_acos=ieee754_acos --name my_atan=atan --name my_log10=ieee754_log10 \
    --header "Set 1: exp, log, pow, sin, cos, tan, asin, acos, atan from glibc's FMA variants; log10 generic." \
    --header "$LG" --header "$WB"
$X groovy "$HERE/src/cpp/Glibm2.groovy" $G/s_expm1.c.254t.optimized $G/e_sinh.c.254t.optimized $G/e_cosh.c.254t.optimized \
    $G/s_tanh.c.254t.optimized $G/s_log1p.c.254t.optimized $G/e_log2.c.254t.optimized $G/e_atan2.c.254t.optimized \
    $G/s_cbrt.c.254t.optimized $G/e_hypot.c.254t.optimized --tables "$W/tables.json" --table-prefix T2_ --package cpp \
    --rt Glibm2Rt --name my_expm1=expm1_t --name my_sinh=sinh_t --name my_cosh=cosh_t --name my_tanh=tanh_t \
    --name my_log1p=log1p_t --name my_log2=log2_t --name my_atan2=atan2_t --name my_cbrt=cbrt_t --name my_hypot=hypot_t \
    --name my_exp_fma=Glibm.exp \
    --header "Set 2: atan2, log2, sinh, tanh, expm1 and log1p from glibc's FMA variants (sysdeps/x86_64/fpu/multiarch, -mfma -mavx2);" \
    --header "cosh, cbrt and hypot generic. sinh and cosh call set 1's exp (Glibm). The public functions at the end pin down NaN results." \
    --header "$LG" --header "$WB"

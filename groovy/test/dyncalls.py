#!/usr/bin/env python3
"""dyncalls.py JAVAP_TXT : the dynamic Groovy call sites (ScriptBytecodeAdapter, DefaultTypeTransformation, NumberNumber*,
InvokerHelper, DefaultGroovyMethods) in javap -c output, per class and method, leaving out the methods Groovy generates
for every class (methodMissing, propertyMissing, $getStaticMetaClass, the this$dist$ forwarders of inner classes, ...).
Used by dynamic_calls.sh. Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../README.md."""
import re, sys, collections
SYN = re.compile(r'this\$dist\$|\$static_methodMissing|\$static_propertyMissing|methodMissing|propertyMissing|\$getStaticMetaClass|getMetaClass|setMetaClass|\$getLookup|invokeMethod|getProperty|setProperty|\$getCallSiteArray|\$createCallSiteArray')
DYN = re.compile(r'(ScriptBytecodeAdapter\.\w+|DefaultTypeTransformation\.\w+|NumberNumber\w+\.\w+|InvokerHelper\.\w+|DefaultGroovyMethods\.\w+|ShortTypeHandling\.\w+)')
cls = meth = None
per = collections.Counter(); kinds = collections.Counter(); ex = {}
for line in open(sys.argv[1], errors='replace'):
    if re.match(r'^(public |final |abstract |class |interface )', line) and '{' in line:
        cls = re.search(r'(?:class|interface) (\S+)', line).group(1); continue
    m = re.match(r'^  (\S.*\));\s*$', line) or re.match(r'^  (static \{\});', line)
    if m: meth = m.group(1); continue
    d = DYN.search(line)
    if d and meth and not SYN.search(meth):
        k = d.group(1)
        if k in ('ScriptBytecodeAdapter.initMetaClass',): continue
        per[(cls, meth.split('(')[0].split()[-1])] += 1; kinds[k] += 1; ex.setdefault(k, (cls, meth))
for k, v in kinds.most_common(): print('%6d %-50s e.g. %s %s' % (v, k, ex[k][0], ex[k][1][:100]))
print()
for (c, m), v in per.most_common(int(sys.argv[2]) if len(sys.argv) > 2 else 30): print('%6d %s.%s' % (v, c, m))

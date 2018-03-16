#!/usr/bin/env python

from __future__ import absolute_import
import os
import shlex
import shutil
import subprocess
import sys
import tempfile

wrapper_name=os.path.basename(sys.argv[0])
release_base=os.path.dirname(os.path.dirname(os.path.realpath(sys.argv[0])))
JAVA_CLASS=None
with open(release_base + "/mains/" + wrapper_name) as f:
    JAVA_CLASS=f.readline().rstrip('\n')

java_tmpdir=tempfile.mkdtemp(prefix="wrapper-generator-")
try:
    javahome=os.getenv("JAVA_HOME")
    if not javahome:
        sys.stderr.write("JAVA_HOME must be set\n")
        sys.exit(1)
    opts=os.getenv("WRAPPER_GENERATOR_JVM_OPTS")
    if opts:
        optsl = shlex.split(opts)
    else:
        optsl = []
    cmd = [javahome + "/bin/java"] + optsl + [
        "-Djava.io.tmpdir=" + java_tmpdir,
        "-classpath",
        javahome + "/lib/tools.jar:" + release_base + "/lib/*",
        JAVA_CLASS] + sys.argv[1:]
    p = subprocess.Popen(cmd)
    while(1):
        try:
            p.wait()
            break
        except KeyboardInterrupt as e:
            pass
    sys.exit(p.returncode)
finally:
    shutil.rmtree(java_tmpdir)

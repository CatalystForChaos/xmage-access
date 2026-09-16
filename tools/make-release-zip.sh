#!/bin/sh
# Assemble the release ZIP that gets attached to a GitHub release.
#
# The ZIP is what a user downloads: everything in dist/ except META-INF/,
# which is a leftover of XMage's own launcher JAR and has never been part
# of the bundle. Everything sits under one xmage-accessible/ folder, the
# layout every release since v0.1.0 has had and the one the install
# instructions in README-accessible.txt describe.
#
# Run it after copying the freshly built target/xmage-access-0.1.0.jar
# over dist/xmage-access-0.1.0.jar and after updating dist/CHANGELOG.txt.
# Anything in dist/ is shipped, so a new file only has to be put there.
#
# Output: target/xmage-accessible.zip
#
# jar comes from the JDK 8 the agent is built with anyway; zip is not
# installed on the Windows box this is cut on.
set -e

cd "$(dirname "$0")/.."

stage=target/zip-stage
out=target/xmage-accessible.zip

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/jar" ]; then
    jar="$JAVA_HOME/bin/jar"
else
    jar=jar
fi

rm -rf "$stage" "$out"
mkdir -p "$stage/xmage-accessible"
cp -r dist/. "$stage/xmage-accessible/"
rm -rf "$stage/xmage-accessible/META-INF"

# The .command scripts are macOS shell scripts and must go out with LF
# endings. git stores them that way, but a Windows checkout turns them
# into CRLF (core.autocrlf), and releases are cut on Windows - v0.1.13's
# bundle shipped "#!/bin/bash\r", which macOS reads as an interpreter
# name ending in a carriage return and refuses to run. Stripping the CRs
# here makes the ZIP right whoever builds it. The .cmd and .bat files
# keep CRLF; that is what Windows expects.
for script in "$stage/xmage-accessible"/*.command; do
    [ -f "$script" ] || continue
    tr -d '\r' < "$script" > "$script.lf"
    mv "$script.lf" "$script"
    chmod +x "$script"
done

"$jar" cfM "$out" -C "$stage" xmage-accessible
rm -rf "$stage"

echo "Wrote $out"
"$jar" tf "$out" | sort

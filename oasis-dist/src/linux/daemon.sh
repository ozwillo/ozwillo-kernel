#! /bin/sh

cd $(dirname $0)

exec java $JAVA_OPTS -cp 'lib/*' oasis.web.WebApp "$@"

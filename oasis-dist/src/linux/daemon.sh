#! /bin/sh

cd $(dirname $0)

exec java -cp 'lib/*' oasis.web.WebApp

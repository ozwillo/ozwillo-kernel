#! /bin/sh
### BEGIN INIT INFO
# Provides:          @oasis.service@
# Required-Start:    $remote_fs $syslog
# Required-Stop:     $remote_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: @oasis.desc@ initscript
# Description:       This file should be used to construct scripts to be
#                    placed in /etc/init.d.
### END INIT INFO

# Do NOT "set -e"

# PATH should only include /usr/* if it runs after the mountnfs.sh script
PATH=/sbin:/usr/sbin:/bin:/usr/bin
DESC="@oasis.desc@"
NAME="@oasis.service@"
DAEMON="@oasis.home@/@oasis.service@"
PIDFILE=/var/run/@oasis.service@.pid
SCRIPTNAME=/etc/init.d/@oasis.service@

SERVICE_USER="@oasis.user@"
SERVICE_GROUP="@oasis.group@"
CONF_DIR="/etc/@oasis.service@"
LOG_DIR="/var/log/@oasis.service@"
EXTRA_ARGS=""
JAVA_OPTS=""

# Exit if the package is not installed
[ -x "$DAEMON" ] || exit 0

# Read configuration variable file if it is present
[ -r /etc/default/$NAME ] && . /etc/default/$NAME

DAEMON_ARGS="-l $CONF_DIR/log4j2.xml -c $CONF_DIR/oasis.conf $EXTRA_ARGS"

export LOG_DIR

TIMESTAMP=`date +%Y%m%d-%H%M%S`
export JAVA_OPTS="-Xloggc:$LOG_DIR/gc-$TIMESTAMP.log -XX:+PrintGCDetails $JAVA_OPTS"

# Load the VERBOSE setting and other rcS variables
. /lib/init/vars.sh

. /lib/lsb/init-functions

VERBOSE="yes"

#
# Function that starts the daemon/service
#
do_start()
{
	# Return
	#   0 if daemon has been started
	#   1 if daemon was already running
	#   2 if daemon could not be started
	start-stop-daemon --start --quiet --pidfile $PIDFILE --startas $DAEMON \
	  -c $SERVICE_USER -g $SERVICE_GROUP --background --make-pidfile --test > /dev/null \
		|| return 1
	start-stop-daemon --start --quiet --pidfile $PIDFILE --startas $DAEMON \
		-c $SERVICE_USER -g $SERVICE_GROUP --background --make-pidfile --no-close \
		-- $DAEMON_ARGS >"$LOG_DIR/$NAME.stdout" 2>"$LOG_DIR/$NAME.stderr" \
		|| return 2
}

#
# Function that stops the daemon/service
#
do_stop()
{
	# Return
	#   0 if daemon has been stopped
	#   1 if daemon was already stopped
	#   2 if daemon could not be stopped
	#   other if a failure occurred
	start-stop-daemon --stop --quiet --retry=INT/10/KILL/5 --pidfile $PIDFILE --oknodo \
		-c $SERVICE_USER -g $SERVICE_GROUP
	RETVAL="$?"
	[ "$RETVAL" = 2 ] && return 2
	# Many daemons don't delete their pidfiles when they exit.
	rm -f $PIDFILE
	return "$RETVAL"
}

case "$1" in
  start)
	[ "$VERBOSE" != no ] && log_daemon_msg "Starting $DESC" "$NAME"
	do_start
	case "$?" in
		0|1) [ "$VERBOSE" != no ] && log_end_msg 0 ;;
		2) [ "$VERBOSE" != no ] && log_end_msg 1 ;;
	esac
	;;
  stop)
	[ "$VERBOSE" != no ] && log_daemon_msg "Stopping $DESC" "$NAME"
	do_stop
	case "$?" in
		0|1) [ "$VERBOSE" != no ] && log_end_msg 0 ;;
		2) [ "$VERBOSE" != no ] && log_end_msg 1 ;;
	esac
	;;
  status)
	status_of_proc "$DAEMON" "$NAME" && exit 0 || exit $?
	;;
  restart|force-reload)
	#
	# If the "reload" option is implemented then remove the
	# 'force-reload' alias
	#
	log_daemon_msg "Restarting $DESC" "$NAME"
	do_stop
	case "$?" in
	  0|1)
		do_start
		case "$?" in
			0) log_end_msg 0 ;;
			1) log_end_msg 1 ;; # Old process is still running
			*) log_end_msg 1 ;; # Failed to start
		esac
		;;
	  *)
		# Failed to stop
		log_end_msg 1
		;;
	esac
	;;
  *)
	echo "Usage: $SCRIPTNAME {start|stop|status|restart|force-reload}" >&2
	exit 3
	;;
esac

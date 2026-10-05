#!/bin/sh
set -eu

cp /etc/rocketmq/broker.conf /tmp/big-market-broker.conf
printf '\nbrokerIP1=%s\n' "${ROCKETMQ_BROKER_IP:-127.0.0.1}" >> /tmp/big-market-broker.conf
exec sh mqbroker -n rocketmq-namesrv:9876 -c /tmp/big-market-broker.conf

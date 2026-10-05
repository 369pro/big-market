#!/bin/sh
set -eu

for topic in activity_sku_stock_zero send_award send_rebate credit_adjust_success strategy_award_stock_deduct; do
    sh mqadmin updateTopic -n rocketmq-namesrv:9876 -b rocketmq-broker:10911 -t "$topic" -r 4 -w 4
done

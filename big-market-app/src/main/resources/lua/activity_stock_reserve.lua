-- KEYS: stock counter, business reservation. ARGV: SKU, retention milliseconds.
local previous = redis.call('GET', KEYS[2])
if previous then
    if previous ~= ARGV[1] then return -2 end
    return 1
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock or stock <= 0 then return 0 end
redis.call('DECR', KEYS[1])
local saved, result = pcall(redis.call, 'SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
if not saved then
    -- Do not leave a stock decrement behind when Redis rejects the
    -- reservation write (for example because of an invalid TTL or a write
    -- failure). The script is atomic, so the rollback is safe here.
    redis.call('INCR', KEYS[1])
    return -3
end
return 1

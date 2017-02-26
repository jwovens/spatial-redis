# spatial-redis

This example Java code shows how use to Redis Geo utilities to store a basic geospatial index and use this index to reference other objects in the cache.

## download redis

Redis geospatial is supported in versions 3.* and can be downloaded from https://redis.io/download .

By default, the Redis server will run on port 6359. Make sure you can connect to Redis locally by opening two shells / command prompts.  Launch the server in one, and the command line client in the other.


```bash
~ $redis-server
```

```bash
~ $redis-cli
```

## java code

This is a Maven project, and the thing to note is the two dependencies that the pom includes.
* Jedis is the Java client, providing access to Redis commands within Java.
* Geohash is a set of utilities for encoding/decoding lat/lon pairs.

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>2.9.0</version>
</dependency>
<dependency>
    <groupId>ch.hsr</groupId>
    <artifactId>geohash</artifactId>
    <version>1.0.10</version>
</dependency>
```

The example code is extremely basic, populating and then querying Redis.  But nonetheless, here is an explanation of the important bits.

* The Redis command, set, stores a key:value pair in the cache.  The base32 encoding is used as the unique key, which defines the point in space e.g. "6xn3nmy564" is actually (-5.417059063911438,-58.42795372009277).  The value is set arbitrarily in this example, but would be whatever data was needed to be stored withh the location. The application sets up 100,000 random entries in the cache.
```
GeoHash geo = GeoHash.withCharacterPrecision(latitude, longitude, hashPrecision);
connection.set(geo.toBase32(), geo.toString());
```
* The Redis command, geoadd, creates (or adds to) a geospatial index, using the longitude and latitude as the unique entry in the index. The 4th parameter is the name of the entry in the index and can be anything useful. The index is a separate data structure to the 100,000 locations; it is a sorted set and represents a single data structure in the cache (see https://redis.io/commands/geoadd).  So the cache now contains 100,001 objects.
```
connection.geoadd(indexName, longitude, latitude, geo.toBase32());
```

In general, the index created through geoadd may be sufficient for an entire application, because any information may be passed as the 4th parameter and stored in the index.  i.e. we may not have needed Set and 100,000 distinct objects. However, this can make the index prohibitively large and non-performant.

Instead, this example creates a minimal spatial index. As the value in the index is the base32 encoding Geohash, the index points directly at the 100,000 database entires populated using the Set command.

* With reference to the index and the dataset together, this example performs an efficient geospatial lookup.  Here, the georadius command was used to query the index.  The value coming back from the index is the key for retrieving the exact object out of 100,000 from the cache.  Though georadius is used to query the index, other geo-commands are available.
```
List<GeoRadiusResponse> nearHashes = conn.georadius(indexName, queryLon, queryLat, queryRadius, GeoUnit.KM, GeoRadiusParam.geoRadiusParam()
        .withCoord()
        .withDist()
        .sortAscending()
        .count(queryNeighbours));

for (GeoRadiusResponse hash: nearHashes) {
    System.out.printf("%5.2f km: %s\n", hash.getDistance() , conn.get(hash.getMemberByString()) );
}
```
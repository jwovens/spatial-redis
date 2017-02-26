package main;

import ch.hsr.geohash.GeoHash;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.geo.GeoRadiusParam;

import java.util.List;
import java.util.Random;

/**
 * Created by jim on 25/02/17.
 */
public class Application {

    static int hashPrecision = 10;
    static String indexName = "my-index";
    static int queryRadius = 1000;
    static int queryNeighbours = 10;

    public static void main(String[] args) {

        Jedis conn = new Jedis("localhost", 6379);
        conn.select(0);

//        repopulateDatabase(conn, Math.pow(10,5)); // populate database with n random lat/lon pairs

        double queryLat = -3.8858;
        double queryLon = -54.4262 ;

        // Query by radius, geo-spatial against the index
        List<GeoRadiusResponse> nearHashes = conn.georadius(indexName, queryLon, queryLat, queryRadius, GeoUnit.KM, GeoRadiusParam.geoRadiusParam()
                .withCoord()
                .withDist()
                .sortAscending()
                .count(queryNeighbours));

        // Having found the nearest from the geo-spatial index, can go back in to the DB with the key(s)
        for (GeoRadiusResponse hash: nearHashes) {
            System.out.printf("%5.2f km: %s\n", hash.getDistance() , conn.get(hash.getMemberByString()) );
        }


        // This is the way to do it using a serverside Lua script
        String luaScript = "local val = redis.call('georadius',KEYS[1],KEYS[2],KEYS[3],KEYS[4],KEYS[5],KEYS[6],KEYS[7]) return redis.call('mget', unpack(val))";
        String[] params = new String[]{indexName, Double.toString(queryLon), Double.toString(queryLat), Double.toString(queryRadius), "km",  "COUNT",  Integer.toString(queryNeighbours)};

        String res = conn.eval(luaScript,params.length,params).toString();
        System.out.println(res);
    }

    private static void repopulateDatabase(Jedis connection, double numberEntries) {
        connection.flushDB();
        double latitude;
        double longitude;

        for (int i=0; i< numberEntries; i++){

            // Pick random lat/lon pair to populate database
            latitude = randomDouble(-85,85);
            longitude = randomDouble(-180,180);
            GeoHash geo = GeoHash.withCharacterPrecision(latitude, longitude, hashPrecision);

            // Store string representation of coordinates against geohash key as separate entities
            connection.set(geo.toBase32(), geo.toString());
            // Store geo-spatial index against value.  Here, value is the key we retrieve by, from the database.
            connection.geoadd(indexName, longitude, latitude, geo.toBase32());
        }

    }

    private static double randomDouble(double rangeMin, double rangeMax) {

        Random r = new Random();
        return rangeMin + (rangeMax - rangeMin) * r.nextDouble();

    }

}

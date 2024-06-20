package org.phobrain.util;

/*
 *  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: MIT-0
 */

// adapted coding example/solution from the net

import java.util.List;
import java.util.LinkedList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;

public class MapUtil {

    public static 
        <K, V extends Comparable<? super V>> Map<K, V>
        sortByValue( Map<K, V> map) {

        return ( sortByValue(map, false) );
    }

    public static 
        <K, V extends Comparable<? super V>> Map<K, V> 
        sortByValue( Map<K, V> map, boolean reverse )
    {
        List<Map.Entry<K, V>> list =
            new LinkedList<Map.Entry<K, V>>( map.entrySet() );
        Collections.sort( list, new Comparator<Map.Entry<K, V>>()
        {
            public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
            {
                int cmp = (o1.getValue()).compareTo( o2.getValue());
                if (reverse) {
                    cmp = -cmp;
                }
                return cmp;
            }
        } );

        Map<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list)
        {
            result.put( entry.getKey(), entry.getValue() );
        }
        return result;
    }
}

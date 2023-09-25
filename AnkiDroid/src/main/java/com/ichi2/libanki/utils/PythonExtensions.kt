/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.utils

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.utils.deepClone
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

fun <T> MutableList<T>.append(value: T) {
    this.add(value)
}

fun <T> MutableList<T>.extend(elements: Iterable<T>) {
    this.addAll(elements)
}

fun <T> len(l: Sequence<T>): Long {
    return l.count().toLong()
}

fun <T> len(l: List<T>): Int {
    return l.size
}

fun len(l: JSONArray): Long {
    return l.length().toLong()
}

fun <E> MutableList<E>.pop(i: Int): E {
    return this.removeAt(i)
}

fun <K, V> HashMap<K, V>.items(): List<Pair<K, V>> {
    return this.entries.map {
        Pair(it.key, it.value)
    }
}

fun <T> List<T>?.isNullOrEmpty(): Boolean {
    return this == null || this.isEmpty()
}

fun <T> list(vararg elements: T) = mutableListOf(elements)

fun <T> list(values: Collection<T>): List<T> = ArrayList(values)

fun <T> set(values: List<T>): HashSet<T> = HashSet(values)

fun String.join(values: Iterable<String>): String {
    return values.joinToString(this)
}

fun <E> MutableList<E>.toJsonArray(): JSONArray {
    val array = JSONArray()
    for (i in this) {
        array.put(i)
    }
    return array
}

fun JSONArray.remove(jsonObject: JSONObject) {
    val index = this.index(jsonObject)
    if (!index.isPresent) {
        throw IllegalArgumentException("Could not find $jsonObject")
    }
    this.remove(index.get())
}

fun JSONArray.index(jsonObject: JSONObject): Optional<Int> {
    this.jsonObjectIterable().forEachIndexed {
            i, value ->
        run {
            if (jsonObject == value) {
                return Optional.of(i)
            }
        }
    }
    return Optional.empty()
}

operator fun JSONObject.set(s: String, value: String) {
    this.put(s, value)
}

fun JSONArray.append(jsonObject: JSONObject) {
    this.put(jsonObject)
}

/**
 * Insert an item at a given position. O(n) at the first position
 *
 * The first argument is the index of the element before which to insert,
 * so `a.insert(0, x)` inserts at the front of the list,
 * and `a.insert(len(a), x)` is equivalent to `a.append(x)`.
 */
fun JSONArray.insert(idx: Int, jsonObject: JSONObject) {
    if (idx >= this.length()) {
        this.put(jsonObject)
        return
    }

    // shuffle the elements up to make room for the next
    // pointer starts at the last element, and appends, after that, replaces elements
    var pointerIndex = this.length() - 1
    while (pointerIndex >= idx) {
        this.put(pointerIndex + 1, this.getJSONObject(pointerIndex))
        pointerIndex--
    }

    this.put(idx, jsonObject)
}

fun JSONArray.jsonObjectIterable(): Iterable<JSONObject> {
    return Iterable { jsonObjectIterator() }
}

fun JSONArray.jsonObjectIterator(): Iterator<JSONObject> {
    return object : Iterator<JSONObject> {
        private var mIndex = 0
        override fun hasNext(): Boolean {
            return mIndex < length()
        }

        override fun next(): JSONObject {
            val `object` = getJSONObject(mIndex)
            mIndex++
            return `object`
        }
    }
}

/** deep clone this into clone.
 *
 * Given a subtype [T] of JSONObject, and a JSONObject [clone], we could do
 * ```
 * T t = new T();
 * clone.deepClonedInto(t);
 * ```
 * in order to obtain a deep clone of [clone] of type [T].  */
@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
fun <T : JSONObject> JSONObject.deepClonedInto(clone: T): T {
    for (key in this.keys()) {
        val value = when (get(key)) {
            is JSONObject -> getJSONObject(key).deepClone()
            is JSONArray -> getJSONArray(key).deepClone()
            else -> get(key)
        }
        clone.put(key, value)
    }
    return clone
}

@CheckResult
fun JSONObject.deepClone(): JSONObject = deepClonedInto(JSONObject())

/**
 * @return Given an array of objects, return the array of the value with `key`, assuming that they are String.
 * E.g. templates, fields are a JSONArray whose objects have name
 */
fun JSONArray.toStringList(key: String?): List<String> {
    val l: MutableList<String> = ArrayList(length())
    for (`object` in jsonObjectIterable()) {
        l.add(`object`.getString(key!!))
    }
    return l
}

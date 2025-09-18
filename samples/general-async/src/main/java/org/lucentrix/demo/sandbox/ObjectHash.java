package org.lucentrix.demo.sandbox;

public class ObjectHash {

    public static void main(String[] args) {
        int hash;
        Object obj;
        //Integer.MAX_VALUE
        for (int i = 0; i < 10; i++) {
            obj = new Object();
            hash = obj.hashCode();
            System.out.println(hash+" : "+System.identityHashCode(obj));
        }
    }
}

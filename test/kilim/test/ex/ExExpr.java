package kilim.test.ex;

class ExInts {
    int[] intstuff() {
        int i = 10;
        int[] arr = new int[10];
        arr[1] = arr[2] ^ i * 34 - ((arr[3] << 2) / 4);
        int j = arr[1];
        int k = i;
        if (i > j  && intr(i) >= k || 
                i < j && i != arr[2] && i == arr[3]) {
            return null;
        }
        return arr;
    }
    int intr(int i) {
        if (((i + 5)  % 6 > 99) || (-i < 3)) {
            return -1;
        } else {
            char c = '\u33d3';
            return (int)c;
        }
    }
    int bits(int i, int j) {
        return (~i | j) & (i >>> 2) & (j >> 3);
    }
}


class ExLongs {
    long[] longstuff() {
        long i = 10;
        long[] arr = new long[10];
        arr[1] = arr[2] ^ i * 34 - ((arr[3] << 2) / 4);
        long j = arr[1];
        long k = i;
        if (i > -j  && longr(i) >= k || 
                i < j && i != arr[2] - 3L && i == arr[3]) {
            return null;
        }
        return arr;
    }
    long longr(long i) {
        if (((i + 5)  % 6 > 99) || (i < 3)) {
            return -1;
        } else {
            char c = '\u33d3';
            return (long)c;
        }
    }
    long bits(long i, long j) {
        return (~i | j) & (i >>> 2) & (j >> 3);
    }
}



class ExDoubles {
    double[] doublestuff() {
        double i = 0;
        
        double[] arr = new double[10];
        arr[1] = (arr[2] * 34)/3;
        double j = arr[1];
        double k = 1;
        if (i > j  && doubler(i) >= k % 5 || 
                i < j && i != arr[2] && i == arr[3]) {
            return null;
        }
        return arr;
    }
    double doubler(double i) {
        if (((i + 5)  % 6 > 99) || (i - 2.0 < 3)) {
            return -1;
        } else {
            char c = '\u33d3';
            return (double)c;
        }
    }
}

class ExFloats {
    float[] floatstuff() {
        float i = 0;
        
        float[] arr = new float[10];
        arr[1] = (arr[2] * 34)/3;
        float j = arr[1];
        float k = 1;
        if (i > j  && floatr(i) >= k % 5 || 
                i < j && i - 3  != arr[2] && i == arr[3]) {
            return null;
        }
        return arr;
    }
    float floatr(float i) {
        if (((i + 5)  % 6 > 99) || (i - 1.0f < 3)) {
            return -1;
        } else {
            char c = '\u33d3';
            return (float)c;
        }
    }
}



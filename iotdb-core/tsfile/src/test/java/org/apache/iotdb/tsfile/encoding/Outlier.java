package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
public class Outlier {

    public static int getBitWith(int num) {
        if (num == 0) return 1;
        else return 32 - Integer.numberOfLeadingZeros(num);
    }

    public static byte[] int2Bytes(int integer) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (integer >> 24);
        bytes[1] = (byte) (integer >> 16);
        bytes[2] = (byte) (integer >> 8);
        bytes[3] = (byte) integer;
        return bytes;
    }

    public static byte[] double2Bytes(double dou) {
        long value = Double.doubleToRawLongBits(dou);
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) ((value >> 8 * i) & 0xff);
        }
        return bytes;
    }

    public static double bytes2Double(ArrayList<Byte> encoded, int start, int num) {
        if (num > 8) {
            System.out.println("bytes2Doubleerror");
            return 0;
        }
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) (encoded.get(i + start) & 0xff)) << (8 * i);
        }
        return Double.longBitsToDouble(value);
    }

    public static byte[] float2bytes(float f) {
        int fbit = Float.floatToIntBits(f);
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            b[i] = (byte) (fbit >> (24 - i * 8));
        }
        int len = b.length;
        byte[] dest = new byte[len];
        System.arraycopy(b, 0, dest, 0, len);
        byte temp;
        for (int i = 0; i < len / 2; ++i) {
            temp = dest[i];
            dest[i] = dest[len - i - 1];
            dest[len - i - 1] = temp;
        }
        return dest;
    }

    public static float bytes2float(ArrayList<Byte> b, int index) {
        int l;
        l = b.get(index);
        l &= 0xff;
        l |= ((long) b.get(index + 1) << 8);
        l &= 0xffff;
        l |= ((long) b.get(index + 2) << 16);
        l &= 0xffffff;
        l |= ((long) b.get(index + 3) << 24);
        return Float.intBitsToFloat(l);
    }

    public static int bytes2Integer(ArrayList<Byte> encoded, int start, int num) {
        int value = 0;
        if (num > 4) {
            System.out.println("bytes2Integer error");
            return 0;
        }
        for (int i = 0; i < num; i++) {
            value <<= 8;
            int b = encoded.get(i + start) & 0xFF;
            value |= b;
        }
        return value;
    }

    public static byte[] bitPacking(ArrayList<Integer> numbers, int bit_width) {
        int block_num = numbers.size() / 8;
        byte[] result = new byte[bit_width * block_num];
        for (int i = 0; i < block_num; i++) {
            for (int j = 0; j < bit_width; j++) {
                int tmp_int = 0;
                for (int k = 0; k < 8; k++) {
                    tmp_int += (((numbers.get(i * 8 + k) >> j) % 2) << k);
                }
                result[i * bit_width + j] = (byte) tmp_int;
            }
        }
        return result;
    }

    public static byte[] bitPacking(ArrayList<ArrayList<Integer>> numbers, int index, int bit_width) {
        int block_num = numbers.size() / 8;
        byte[] result = new byte[bit_width * block_num];
        for (int i = 0; i < block_num; i++) {
            for (int j = 0; j < bit_width; j++) {
                int tmp_int = 0;
                for (int k = 0; k < 8; k++) {
                    tmp_int += (((numbers.get(i * 8 + k + 1).get(index) >> j) % 2) << k);
                }
                result[i * bit_width + j] = (byte) tmp_int;
            }
        }
        return result;
    }

    public static ArrayList<Integer> decodebitPacking(
            ArrayList<Byte> encoded, int decode_pos, int bit_width, int min_delta, int block_size) {
        ArrayList<Integer> result_list = new ArrayList<>();
        for (int i = 0; i < (block_size - 1) / 8; i++) { // bitpacking  纵向8个，bit width是多少列
            int[] val8 = new int[8];
            for (int j = 0; j < 8; j++) {
                val8[j] = 0;
            }
            for (int j = 0; j < bit_width; j++) {
                byte tmp_byte = encoded.get(decode_pos + bit_width - 1 - j);
                byte[] bit8 = new byte[8];
                for (int k = 0; k < 8; k++) {
                    bit8[k] = (byte) (tmp_byte & 1);
                    tmp_byte = (byte) (tmp_byte >> 1);
                }
                for (int k = 0; k < 8; k++) {
                    val8[k] = val8[k] * 2 + bit8[k];
                }
            }
            for (int j = 0; j < 8; j++) {
                result_list.add(val8[j] + min_delta);
            }
            decode_pos += bit_width;
        }
        return result_list;
    }

    public static int part(ArrayList<Integer> arr, int index, int low, int high) {
        int tmp = arr.get(low);
        while (low < high) {
            while (low < high
                    && (arr.get(high) > tmp)) {
                high--;
            }
            arr.set(low, arr.get(high));
            while (low < high
                    && (arr.get(low) < tmp)) {
                low++;
            }
            arr.set(high, arr.get(low));
        }
        arr.set(low, tmp);
        return low;
    }

    public static void quickSort(ArrayList<Integer> arr, int index, int low, int high) {
        Stack<Integer> stack = new Stack<>();
        int mid = part(arr, index, low, high);
        if (mid + 1 < high) {
            stack.push(mid + 1);
            stack.push(high);
        }
        if (mid - 1 > low) {
            stack.push(low);
            stack.push(mid - 1);
        }
        while (stack.empty() == false) {
            high = stack.pop();
            low = stack.pop();
            mid = part(arr, index, low, high);
            if (mid + 1 < high) {
                stack.push(mid + 1);
                stack.push(high);
            }
            if (mid - 1 > low) {
                stack.push(low);
                stack.push(mid - 1);
            }
        }
    }

    public static int getCommon(int m, int n) {
        int z;
        while (m % n != 0) {
            z = m % n;
            m = n;
            n = z;
        }
        return n;
    }

    public static void splitTimeStamp3(
            ArrayList<ArrayList<Integer>> ts_block, ArrayList<Integer> result) {
        int td_common = 0;
        for (int i = 1; i < ts_block.size(); i++) {
            int time_diffi = ts_block.get(i).get(0) - ts_block.get(i - 1).get(0);
            if (td_common == 0) {
                if (time_diffi != 0) {
                    td_common = time_diffi;
                    continue;
                } else {
                    continue;
                }
            }
            if (time_diffi != 0) {
                td_common = getCommon(time_diffi, td_common);
                if (td_common == 1) {
                    break;
                }
            }
        }
        if (td_common == 0) {
            td_common = 1;
        }

        int t0 = ts_block.get(0).get(0);
        for (int i = 0; i < ts_block.size(); i++) {
            ArrayList<Integer> tmp = new ArrayList<>();
            int interval_i = (ts_block.get(i).get(0) - t0) / td_common;
            tmp.add(t0 + interval_i);
            tmp.add(ts_block.get(i).get(1));
            ts_block.set(i, tmp);
        }
        result.add(td_common);
    }

    public static ArrayList<ArrayList<Integer>> getEncodeBitsRegression(
            ArrayList<ArrayList<Integer>> ts_block,
            int block_size,
            ArrayList<Integer> result,
            ArrayList<Integer> i_star,
            double threshold) {
        int timestamp_delta_min = Integer.MAX_VALUE;
        int value_delta_min = Integer.MAX_VALUE;
        ArrayList<ArrayList<Integer>> ts_block_delta = new ArrayList<>();

        ArrayList<Integer> tmp0 = new ArrayList<>();
        tmp0.add(ts_block.get(0).get(0));
        tmp0.add(ts_block.get(0).get(1));
        ts_block_delta.add(tmp0);

        ArrayList<ArrayList<Integer>> epsilon_r_j_list = new ArrayList<>();
        ArrayList<ArrayList<Integer>> epsilon_v_j_list = new ArrayList<>();
        ArrayList<Integer> tmp_top_k = new ArrayList<>();

        // delta to Regression
        for (int j = 1; j < block_size; j++) {
            int epsilon_r = ts_block.get(j).get(0) - ts_block.get(j - 1).get(0);
            int epsilon_v = ts_block.get(j).get(1) - ts_block.get(j - 1).get(1);

            if (epsilon_r < timestamp_delta_min) {
                timestamp_delta_min = epsilon_r;
            }
            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }

            if (epsilon_r_j_list.size() == 0) {
                tmp_top_k = new ArrayList<>();
                tmp_top_k.add(j);
                tmp_top_k.add(epsilon_r);
                epsilon_r_j_list.add(tmp_top_k);
                tmp_top_k = new ArrayList<>();
                tmp_top_k.add(j);
                tmp_top_k.add(epsilon_v);
                epsilon_v_j_list.add(tmp_top_k);
            } else {
                tmp_top_k = new ArrayList<>();
                ;
                tmp_top_k.add(j);
                tmp_top_k.add(epsilon_r);
                // 寻找插入位置
                int insertIndex = 0;
                while (insertIndex < epsilon_r_j_list.size() && tmp_top_k.get(1) < epsilon_r_j_list.get(insertIndex).get(1)) {
                    insertIndex++;
                }
                epsilon_r_j_list.add(insertIndex, tmp_top_k);
                tmp_top_k = new ArrayList<>();
                ;
                tmp_top_k.add(j);
                tmp_top_k.add(epsilon_v);
                insertIndex = 0;
                while (insertIndex < epsilon_v_j_list.size() && tmp_top_k.get(1) < epsilon_v_j_list.get(insertIndex).get(1)) {
                    insertIndex++;
                }
                epsilon_v_j_list.add(insertIndex, tmp_top_k);
            }

            ArrayList<Integer> tmp = new ArrayList<>();
            tmp.add(epsilon_r);
            tmp.add(epsilon_v);
            ts_block_delta.add(tmp);
        }
        int j_star_index_t = (int) ((double) epsilon_r_j_list.size() * threshold);
        int timestamp_delta_topk_max = epsilon_r_j_list.get(j_star_index_t).get(1);
//        timestamp_delta_max = epsilon_r_j_list.get(0).get(1);

        int j_star_index_v = (int) ((double) epsilon_v_j_list.size() * threshold);
        int value_delta_topk_max = epsilon_v_j_list.get(j_star_index_v).get(1);

        int max_interval = Integer.MIN_VALUE;
        int max_interval_i = -1;
        int max_value = Integer.MIN_VALUE;
        int max_value_i = -1;
        for (int j = block_size - 1; j > 0; j--) {
            int epsilon_r = ts_block_delta.get(j).get(0) - timestamp_delta_min;
            int epsilon_v = ts_block_delta.get(j).get(1) - value_delta_min;
            if (epsilon_r > max_interval) {
                max_interval = epsilon_r;
                max_interval_i = j;
            }
            if (epsilon_v > max_value) {
                max_value = epsilon_v;
                max_value_i = j;
            }
            ArrayList<Integer> tmp = new ArrayList<>();
            tmp.add(epsilon_r);
            tmp.add(epsilon_v);
            ts_block_delta.set(j, tmp);
        }


        int max_bit_width_interval = getBitWith(max_interval);
        int max_bit_width_value = getBitWith(max_value);

        // calculate error
        int length = (max_bit_width_interval + max_bit_width_value) * (block_size - 1);
        result.clear();

        result.add(length);
        result.add(max_bit_width_interval);
        result.add(max_bit_width_value);
        result.add(getBitWith(timestamp_delta_topk_max - timestamp_delta_min));
        result.add(getBitWith(value_delta_topk_max - value_delta_min));


        result.add(timestamp_delta_min);
        result.add(value_delta_min);

        i_star.add(max_interval_i);
        i_star.add(max_value_i);

        return ts_block_delta;
    }


    public static ArrayList<Byte> encode2Bytes(
            ArrayList<ArrayList<Integer>> ts_block,
            ArrayList<Integer> raw_length,
            ArrayList<Integer> result2) {
        ArrayList<Byte> encoded_result = new ArrayList<>();

        // encode interval0 and value0
        byte[] interval0_byte = int2Bytes(ts_block.get(0).get(0));
        for (byte b : interval0_byte) encoded_result.add(b);
        byte[] value0_byte = int2Bytes(ts_block.get(0).get(1));
        for (byte b : value0_byte) encoded_result.add(b);

        // encode theta
        byte[] timestamp_min_byte = int2Bytes(raw_length.get(3));
        for (byte b : timestamp_min_byte) encoded_result.add(b);
        byte[] value_min_byte = int2Bytes(raw_length.get(4));
        for (byte b : value_min_byte) encoded_result.add(b);

        // encode interval
        byte[] max_bit_width_interval_byte = int2Bytes(raw_length.get(1));
        for (byte b : max_bit_width_interval_byte) encoded_result.add(b);
        byte[] timestamp_bytes = bitPacking(ts_block, 0, raw_length.get(1));
        for (byte b : timestamp_bytes) encoded_result.add(b);

        // encode value
        byte[] max_bit_width_value_byte = int2Bytes(raw_length.get(2));
        for (byte b : max_bit_width_value_byte) encoded_result.add(b);
        byte[] value_bytes = bitPacking(ts_block, 1, raw_length.get(2));
        for (byte b : value_bytes) encoded_result.add(b);

        byte[] td_common_byte = int2Bytes(result2.get(0));
        for (byte b : td_common_byte) encoded_result.add(b);

        return encoded_result;
    }

    public static ArrayList<Integer> getAbsDeltaTsBlock(
            ArrayList<Integer> ts_block) {
        ArrayList<Integer> ts_block_delta = new ArrayList<>();

        ts_block_delta.add(ts_block.get(0));
        int value_delta_min = Integer.MAX_VALUE;
        for (int i = 1; i < ts_block.size(); i++) {

            int epsilon_v = ts_block.get(i) - ts_block.get(i - 1);

            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }

        }
        for (int i = 1; i < ts_block.size(); i++) {
            int epsilon_v = ts_block.get(i) - value_delta_min - ts_block.get(i - 1);
            ts_block_delta.add(epsilon_v);
        }
        return ts_block_delta;
    }

    public static ArrayList<Integer> getBitWith(ArrayList<Integer> ts_block) {
        ArrayList<Integer> ts_block_bit_width = new ArrayList<>();
        for (int integers : ts_block) {
            ts_block_bit_width.add(getBitWith(integers));
        }
        return ts_block_bit_width;
    }

    public static ArrayList<ArrayList<Integer>> getDeltaTsBlock(
            ArrayList<ArrayList<Integer>> ts_block,
            ArrayList<Integer> result,
            ArrayList<Integer> outlier_top_k_index,
            ArrayList<ArrayList<Integer>> outlier_top_k) {
        ArrayList<ArrayList<Integer>> ts_block_delta = new ArrayList<>();
        ArrayList<Integer> tmp = new ArrayList<>();
        tmp.add(ts_block.get(0).get(0));
        tmp.add(ts_block.get(0).get(1));
        ts_block_delta.add(tmp);
        int timestamp_delta_min = Integer.MAX_VALUE;
        int value_delta_min = Integer.MAX_VALUE;

        for (int i = 1; i < ts_block.size(); i++) {
            int epsilon_r;
            int epsilon_v;
            if (outlier_top_k_index.contains(i)) {
                epsilon_r = 0;
                epsilon_v = 0;
                tmp = new ArrayList<>();
                tmp.add(i);
                tmp.add(ts_block.get(i).get(0));
                tmp.add(ts_block.get(i).get(1));
                outlier_top_k.add(tmp);
            } else {
                epsilon_r = ts_block.get(i).get(0) - ts_block.get(i - 1).get(0);
                epsilon_v = ts_block.get(i).get(1) - ts_block.get(i - 1).get(1);
                if (epsilon_r < timestamp_delta_min) {
                    timestamp_delta_min = epsilon_r;
                }
                if (epsilon_v < value_delta_min) {
                    value_delta_min = epsilon_v;
                }

            }

            tmp = new ArrayList<>();
            tmp.add(epsilon_r);
            tmp.add(epsilon_v);
            ts_block_delta.add(tmp);
        }
        for (int j = ts_block.size() - 1; j > 0; j--) {
            if (!outlier_top_k_index.contains(j)) {
                int epsilon_r = ts_block_delta.get(j).get(0) - timestamp_delta_min;
                int epsilon_v = ts_block_delta.get(j).get(1) - value_delta_min;
                tmp = new ArrayList<>();
                tmp.add(epsilon_r);
                tmp.add(epsilon_v);
                ts_block_delta.set(j, tmp);
            }
        }
//        System.out.println(value_delta_min);
        result.add(timestamp_delta_min);
        result.add(value_delta_min);

        return ts_block_delta;
    }

    public static ArrayList<Byte> encodeDeltaTsBlock(
            ArrayList<ArrayList<Integer>> ts_block_delta, ArrayList<Integer> result, int t_or_v) {
        ArrayList<Byte> encoded_result = new ArrayList<>();

        // encode interval0 and value0
        byte[] interval0_byte = int2Bytes(ts_block_delta.get(0).get(t_or_v));
        for (byte b : interval0_byte) encoded_result.add(b);

        // encode min delta
        byte[] min_interval_byte = int2Bytes(result.get(t_or_v));
        for (byte b : min_interval_byte) encoded_result.add(b);

        int max_interval = Integer.MIN_VALUE;
        int block_size = ts_block_delta.size();

        for (int j = block_size - 1; j > 0; j--) {
            int epsilon_r = ts_block_delta.get(j).get(t_or_v);
            if (epsilon_r > max_interval) {
                max_interval = epsilon_r;
            }
        }

        // encode max bit width
        byte[] timestamp_min_byte = int2Bytes(getBitWith(max_interval));
        for (byte b : timestamp_min_byte) encoded_result.add(b);

        // encode interval
//        System.out.println(getBitWith(max_interval));
        byte[] timestamp_bytes = bitPacking(ts_block_delta, t_or_v, getBitWith(max_interval));
//        System.out.println(timestamp_bytes.length);
        for (byte b : timestamp_bytes) encoded_result.add(b);

        return encoded_result;
    }

    public static int getBitwidthDeltaTsBlock(ArrayList<ArrayList<Integer>> outlier_top_k, int t_or_v) {
        int bit_num = 0;
        int block_size = outlier_top_k.size();
//        System.out.println(outlier_top_k);
//        bit_num += (10 * block_size);
        ArrayList<ArrayList<Integer>> ts_block_delta = new ArrayList<>();
        int timestamp_delta_min = Integer.MAX_VALUE;
        int timestamp_delta_max = Integer.MIN_VALUE;
        for (int i = 1; i < block_size; i++) {
//        for (ArrayList<Integer> integers : outlier_top_k) {
            int epsilon_r = outlier_top_k.get(i).get(t_or_v + 1) - outlier_top_k.get(i - 1).get(t_or_v + 1);
            if (epsilon_r < timestamp_delta_min) {
                timestamp_delta_min = epsilon_r;
            }
            if (epsilon_r > timestamp_delta_max) {
                timestamp_delta_max = epsilon_r;
            }
        }
        bit_num += ((block_size - 1) * getBitWith(timestamp_delta_max - timestamp_delta_min) + 32);
        return bit_num;
    }



    public static ArrayList<Byte> ReorderingRegressionEncoder(
            ArrayList<Integer> data, int block_size, String dataset_name) throws IOException {
        block_size++;
        ArrayList<Byte> encoded_result = new ArrayList<Byte>();
        int length_all = data.size();
        byte[] length_all_bytes = int2Bytes(length_all);
        for (byte b : length_all_bytes) encoded_result.add(b);
        int block_num = length_all / block_size;

        byte[] block_size_byte = int2Bytes(block_size);
        for (byte b : block_size_byte) encoded_result.add(b);



//        for (int i = 0; i < 1; i++) {
        for (int i = 0; i < block_num; i++) {
            ArrayList<Integer> ts_block = new ArrayList<>();
            ArrayList<Integer> ts_block_reorder = new ArrayList<>();
            for (int j = 0; j < block_size; j++) {
                ts_block.add(data.get(j + i * block_size));
                ts_block_reorder.add(data.get(j + i * block_size));
            }
            // time-order
            ArrayList<Byte> cur_encoded_result = learnKDelta(ts_block);
            encoded_result.addAll(cur_encoded_result);

        }

        int remaining_length = length_all - block_num * block_size;
        if (remaining_length == 1) {
            byte[] timestamp_end_bytes = int2Bytes(data.get(data.size() - 1));
            for (byte b : timestamp_end_bytes) encoded_result.add(b);
        }
//        if (remaining_length != 0 && remaining_length != 1) {
//            ArrayList<ArrayList<Integer>> ts_block = new ArrayList<>();
////            ArrayList<ArrayList<Integer>> ts_block_reorder = new ArrayList<>();
//
//            for (int j = block_num * block_size; j < length_all; j++) {
//                ts_block.add(data.get(j));
////                ts_block_reorder.add(data.get(j));
//            }
//            ArrayList<Integer> result2 = new ArrayList<>();
////            ArrayList<Integer> result2_reorder = new ArrayList<>();
//            splitTimeStamp3(ts_block, result2);
////            splitTimeStamp3(ts_block_reorder, result2_reorder);
////            quickSort(ts_block_reorder, 1, 0, remaining_length - 1);
//
//
////
//            quickSort(ts_block, 0, 0, remaining_length - 1);
//            ArrayList<Integer> encoded_length = new ArrayList<>(); // length
//            Result res = learnKDelta(ts_block, encoded_length);
//
//
//            // value-order
//            quickSort(ts_block, 1, 0, remaining_length - 1);
//            ArrayList<Integer> reorder_encoded_length = new ArrayList<>();
//            Result res_reorder =  learnKDelta(ts_block,  reorder_encoded_length);
//
//            if (encoded_length.get(0) <= reorder_encoded_length.get(0)) {
//                quickSort(ts_block, 0, 0, remaining_length - 1);
////                System.out.println("encoded_length.get(0): "+encoded_length.get(0));
//                count_raw++;
//            } else {
//                encoded_length = reorder_encoded_length;
//                res = res_reorder;
//                count_reorder++;
//            }
////            System.out.println(" encoded_length.get(0) : "+ encoded_length.get(0));
//            bits_encoded_data += encoded_length.get(0);
////            ts_block_delta =
////                    getEncodeBitsRegression(ts_block, remaining_length, raw_length, i_star_ready_reorder,threshold);
////            int supple_length;
////            if (remaining_length % 8 == 0) {
////                supple_length = 1;
////            } else if (remaining_length % 8 == 1) {
////                supple_length = 0;
////            } else {
////                supple_length = 9 - remaining_length % 8;
////            }
////            for (int s = 0; s < supple_length; s++) {
////                ArrayList<Integer> tmp = new ArrayList<>();
////                tmp.add(0);
////                tmp.add(0);
////                ts_block_delta.add(tmp);
////            }
////
////            ArrayList<Byte> cur_encoded_result = encode2Bytes(ts_block_delta, raw_length, result2);
////            bits_encoded_data += (cur_encoded_result.size() * 8L);
//////            System.out.println("encoded_result: "+ (cur_encoded_result.size() * 8L));
//////      encoded_result.addAll(cur_encoded_result);
//        }


        return encoded_result;
    }

    public static int getBitwidthOutlier(ArrayList<ArrayList<Integer>> outlier_top_k, int size) {
        int bit_num = 0;
        int block_size = outlier_top_k.size();
        if(block_size <= 2) return block_size*64;

        ArrayList<ArrayList<Integer>> ts_block_delta = new ArrayList<>();
        int timestamp_delta_min = Integer.MAX_VALUE;
        int timestamp_delta_max = Integer.MIN_VALUE;
        int value_delta_min = Integer.MAX_VALUE;
        int value_delta_max = Integer.MIN_VALUE;

        for (int i = 1; i < block_size; i++) {
            int epsilon_t = outlier_top_k.get(i).get(1) - outlier_top_k.get(i - 1).get(1);
            int epsilon_v = outlier_top_k.get(i).get(2) - outlier_top_k.get(i - 1).get(2);

            if (epsilon_t < timestamp_delta_min) {
                timestamp_delta_min = epsilon_t;
            }
            if (epsilon_t > timestamp_delta_max) {
                timestamp_delta_max = epsilon_t;
            }
            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }
            if (epsilon_v > value_delta_max) {
                value_delta_max = epsilon_v;
            }
        }
        bit_num += ((block_size - 1) * getBitWith(timestamp_delta_max - timestamp_delta_min) + 64);
        bit_num += ((block_size - 1) * getBitWith(value_delta_max - value_delta_min) + 64);
        bit_num += Math.min(size, block_size * getBitWith(size)); // need to modify to store sign

        return bit_num;
    }


    public static int getBitwidthOutlierFour(ArrayList<ArrayList<Integer>> outlier_top_k_left_time,
                                             ArrayList<ArrayList<Integer>> outlier_top_k_right_time,
                                             ArrayList<ArrayList<Integer>> outlier_top_k_left_value,
                                             ArrayList<ArrayList<Integer>> outlier_top_k_right_value,int size) {
        int bit_num = 0;
        int block_size = outlier_top_k_left_time.size();
        if(block_size <= 2) bit_num += (block_size*64);
        bit_num += ( block_size * getBitWith(size));

        int timestamp_delta_min = Integer.MAX_VALUE;
        int timestamp_delta_max = Integer.MIN_VALUE;
        int value_delta_min = Integer.MAX_VALUE;
        int value_delta_max = Integer.MIN_VALUE;

        for (int i = 1; i < block_size; i++) {
            int epsilon_t = outlier_top_k_left_time.get(i).get(1) - outlier_top_k_left_time.get(i - 1).get(1);
            int epsilon_v = outlier_top_k_left_time.get(i).get(2) - outlier_top_k_left_time.get(i - 1).get(2);

            if (epsilon_t < timestamp_delta_min) {
                timestamp_delta_min = epsilon_t;
            }
            if (epsilon_t > timestamp_delta_max) {
                timestamp_delta_max = epsilon_t;
            }
            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }
            if (epsilon_v > value_delta_max) {
                value_delta_max = epsilon_v;
            }
        }
        bit_num += ((block_size - 1) * getBitWith(timestamp_delta_max - timestamp_delta_min) + 64);
        bit_num += ((block_size - 1) * getBitWith(value_delta_max - value_delta_min) + 64);
//        bit_num += Math.min(size, block_size * getBitWith(size)); // need to modify to store sign

        block_size = outlier_top_k_right_time.size();
        if(block_size <= 2) bit_num += (block_size*64);
        bit_num += ( block_size * getBitWith(size));

        timestamp_delta_min = Integer.MAX_VALUE;
        timestamp_delta_max = Integer.MIN_VALUE;
        value_delta_min = Integer.MAX_VALUE;
        value_delta_max = Integer.MIN_VALUE;

        for (int i = 1; i < block_size; i++) {
            int epsilon_t = outlier_top_k_right_time.get(i).get(1) - outlier_top_k_right_time.get(i - 1).get(1);
            int epsilon_v = outlier_top_k_right_time.get(i).get(2) - outlier_top_k_right_time.get(i - 1).get(2);

            if (epsilon_t < timestamp_delta_min) {
                timestamp_delta_min = epsilon_t;
            }
            if (epsilon_t > timestamp_delta_max) {
                timestamp_delta_max = epsilon_t;
            }
            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }
            if (epsilon_v > value_delta_max) {
                value_delta_max = epsilon_v;
            }
        }
        bit_num += ((block_size - 1) * getBitWith(timestamp_delta_max - timestamp_delta_min) + 64);
        bit_num += ((block_size - 1) * getBitWith(value_delta_max - value_delta_min) + 64);

        block_size = outlier_top_k_left_value.size();
        if(block_size <= 2) bit_num += (block_size*64);
        bit_num += ( block_size * getBitWith(size));

        timestamp_delta_min = Integer.MAX_VALUE;
        timestamp_delta_max = Integer.MIN_VALUE;
        value_delta_min = Integer.MAX_VALUE;
        value_delta_max = Integer.MIN_VALUE;

        for (int i = 1; i < block_size; i++) {
            int epsilon_t = outlier_top_k_left_value.get(i).get(1) - outlier_top_k_left_value.get(i - 1).get(1);
            int epsilon_v = outlier_top_k_left_value.get(i).get(2) - outlier_top_k_left_value.get(i - 1).get(2);

            if (epsilon_t < timestamp_delta_min) {
                timestamp_delta_min = epsilon_t;
            }
            if (epsilon_t > timestamp_delta_max) {
                timestamp_delta_max = epsilon_t;
            }
            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }
            if (epsilon_v > value_delta_max) {
                value_delta_max = epsilon_v;
            }
        }
        bit_num += ((block_size - 1) * getBitWith(timestamp_delta_max - timestamp_delta_min) + 64);
        bit_num += ((block_size - 1) * getBitWith(value_delta_max - value_delta_min) + 64);

        block_size = outlier_top_k_right_value.size();
        if(block_size <= 2) bit_num += (block_size*64);
        bit_num += ( block_size * getBitWith(size));

        timestamp_delta_min = Integer.MAX_VALUE;
        timestamp_delta_max = Integer.MIN_VALUE;
        value_delta_min = Integer.MAX_VALUE;
        value_delta_max = Integer.MIN_VALUE;

        for (int i = 1; i < block_size; i++) {
            int epsilon_t = outlier_top_k_right_value.get(i).get(1) - outlier_top_k_right_value.get(i - 1).get(1);
            int epsilon_v = outlier_top_k_right_value.get(i).get(2) - outlier_top_k_right_value.get(i - 1).get(2);

            if (epsilon_t < timestamp_delta_min) {
                timestamp_delta_min = epsilon_t;
            }
            if (epsilon_t > timestamp_delta_max) {
                timestamp_delta_max = epsilon_t;
            }
            if (epsilon_v < value_delta_min) {
                value_delta_min = epsilon_v;
            }
            if (epsilon_v > value_delta_max) {
                value_delta_max = epsilon_v;
            }
        }
        bit_num += ((block_size - 1) * getBitWith(timestamp_delta_max - timestamp_delta_min) + 64);
        bit_num += ((block_size - 1) * getBitWith(value_delta_max - value_delta_min) + 64);


        return bit_num;
    }

    public static class Result {
        public ArrayList<ArrayList<Integer>> final_outlier_top_k;
        public ArrayList<Integer> final_outlier_top_k_index;

        public Result(ArrayList<ArrayList<Integer>> final_outlier_top_k, ArrayList<Integer> final_outlier_top_k_index) {
            this.final_outlier_top_k = final_outlier_top_k;
            this.final_outlier_top_k_index = final_outlier_top_k_index;
        }
    }
    private static ArrayList<Byte> learnKDelta(ArrayList<Integer> ts_block) {
        int timestamp_delta_min = Integer.MAX_VALUE;
        int value_delta_min = Integer.MAX_VALUE;
        int block_size = ts_block.size();
        ArrayList<Byte> cur_byte = new ArrayList<>();
        double threshold = 0.2;
        int k_up_bound = (int) ((double)block_size * threshold);
//        System.out.println(k_up_bound);
        int k_down_bound =(int) ((double)block_size * (1 - threshold));
        if(k_down_bound == block_size || k_down_bound == block_size-1){
            k_down_bound = block_size - 2;
        }
//        System.out.println(k_down_bound);
//        System.out.println(block_size);

        ArrayList<Integer> ts_block_delta = getAbsDeltaTsBlock(ts_block);
        ArrayList<Integer> ts_block_bit_width = getBitWith(ts_block_delta);
        quickSort(ts_block_delta, 0, 1, block_size - 1);

        ArrayList<Integer> ts_block_order_value = new ArrayList<>();
        for(int i=1;i<block_size;i++){
            ts_block_order_value.add(ts_block_delta.get(i));
        }

        int k_up_bound_value = ts_block_order_value.get(k_up_bound);
        int k_down_bound_value = ts_block_order_value.get(k_down_bound);

        int max_delta_value = ts_block_order_value.get(block_size-2);
        int max_delta_value_bit_width = getBitWith(max_delta_value);
        ArrayList<Integer> spread_value = new ArrayList<>();
        for(int i=1;i<max_delta_value_bit_width;i++){
            int spread_v = (int) pow(2,i)-1;
            if(spread_v>=k_down_bound_value)
                spread_value.add(spread_v);
        }


        ArrayList<Integer> start_value = new ArrayList<>();
        for(int i=0;i<max_delta_value_bit_width;i++){
            int start_v = (int) pow(2,i);
            if(start_v<=k_up_bound_value)
                start_value.add(start_v);
        }


        int final_k_start_value=ts_block_order_value.get(0);
        int final_k_end_value=ts_block_order_value.get(ts_block_order_value.size()-1);

        int min_bits = 0;
        min_bits +=( getBitWith( final_k_end_value-final_k_start_value)*(block_size-1)+32);
        ArrayList<ArrayList<Integer>> final_outlier_top_k = new ArrayList<>();
        ArrayList<Integer> final_outlier_top_k_index = new ArrayList<>();

        for (int k_start_value : start_value) {
            for (int k_spread_value : spread_value) {
                int k_end_value = k_spread_value + k_start_value;
                ArrayList<ArrayList<Integer>> outlier_top_k = new ArrayList<>();
                ArrayList<ArrayList<Integer>> outlier_top_k_left_value = new ArrayList<>();
                ArrayList<ArrayList<Integer>> outlier_top_k_right_value = new ArrayList<>();


                ArrayList<Integer> outlier_top_k_index = new ArrayList<>();

                ArrayList<Integer> new_ts_block_delta = new ArrayList<>();
                ArrayList<Integer> new_ts_block_bit_width = new ArrayList<>();
                new_ts_block_delta.add(ts_block_delta.get(0)); // Normal point without outlier time
                new_ts_block_bit_width.add(ts_block_bit_width.get(0));// Bit width of Normal point without outlier time
                for (int i = 1; i < block_size; i++) {
                    if ( ts_block_delta.get(i) < k_start_value || ts_block_delta.get(i) > k_end_value) {
                        ArrayList<Integer> tmp = new ArrayList<>();
                        tmp.add(i);
                        tmp.add(ts_block_delta.get(i));
                        outlier_top_k.add(tmp);
                         if ( ts_block_delta.get(i) < k_start_value) {
                            outlier_top_k_left_value.add(tmp);
                        } else if (ts_block_delta.get(i) > k_end_value) {
                            outlier_top_k_right_value.add(tmp);
                        }
                    } else {
                        new_ts_block_delta.add(ts_block_delta.get(i));
                        new_ts_block_bit_width.add(ts_block_bit_width.get(i));
                    }
                }
                int outlier_size = outlier_top_k.size();
                int cur_bits = getBitwidthOutlierTwo(outlier_top_k_left_value,outlier_top_k_right_value,block_size);
                cur_bits += getBitWith(k_spread_value) * (new_ts_block_delta.size() - 1);
                cur_bits += 64; // 0-th
                cur_bits += 64; // min_delta
                if (cur_bits < min_bits) {
                    min_bits = cur_bits;

                    final_k_start_value = k_start_value;
                    final_k_end_value = k_end_value;
                    final_outlier_top_k = outlier_top_k;
                    final_outlier_top_k_index = outlier_top_k_index;
//                            System.out.println(final_outlier_top_k);
//                            System.out.println(final_outlier_top_k_index);
                }

            }
        }


        return cur_byte;
    }

    public static ArrayList<ArrayList<Integer>> ReorderingRegressionDecoder(ArrayList<Byte> encoded) {
        ArrayList<ArrayList<Integer>> data = new ArrayList<>();
        int decode_pos = 0;
        int length_all = bytes2Integer(encoded, decode_pos, 4);
        decode_pos += 4;
        int block_size = bytes2Integer(encoded, decode_pos, 4);
        decode_pos += 4;

        int block_num = length_all / block_size;
        int remain_length = length_all - block_num * block_size;
        int zero_number;
        if (remain_length % 8 == 0) {
            zero_number = 1;
        } else if (remain_length % 8 == 1) {
            zero_number = 0;
        } else {
            zero_number = 9 - remain_length % 8;
        }

        for (int k = 0; k < block_num; k++) {
            ArrayList<Integer> time_list = new ArrayList<>();
            ArrayList<Integer> value_list = new ArrayList<>();

            ArrayList<ArrayList<Integer>> ts_block = new ArrayList<>();

            int time0 = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            int value0 = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;

            float theta0_r = bytes2float(encoded, decode_pos);
            decode_pos += 4;
            float theta1_r = bytes2float(encoded, decode_pos);
            decode_pos += 4;
            float theta0_v = bytes2float(encoded, decode_pos);
            decode_pos += 4;
            float theta1_v = bytes2float(encoded, decode_pos);
            decode_pos += 4;

            int max_bit_width_time = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            time_list = decodebitPacking(encoded, decode_pos, max_bit_width_time, 0, block_size);
            decode_pos += max_bit_width_time * (block_size - 1) / 8;

            int max_bit_width_value = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            value_list = decodebitPacking(encoded, decode_pos, max_bit_width_value, 0, block_size);
            decode_pos += max_bit_width_value * (block_size - 1) / 8;

            int td_common = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;

            int ti_pre = time0;
            int vi_pre = value0;
            for (int i = 0; i < block_size - 1; i++) {
                int ti = (int) ((double) theta1_r * ti_pre + (double) theta0_r + time_list.get(i));
                time_list.set(i, ti);
                ti_pre = ti;

                int vi = (int) ((double) theta1_v * vi_pre + (double) theta0_v + value_list.get(i));
                value_list.set(i, vi);
                vi_pre = vi;
            }

            ArrayList<Integer> ts_block_tmp0 = new ArrayList<>();
            ts_block_tmp0.add(time0);
            ts_block_tmp0.add(value0);
            ts_block.add(ts_block_tmp0);
            for (int i = 0; i < block_size - 1; i++) {
                int ti = (time_list.get(i) - time0) * td_common + time0;
                ArrayList<Integer> ts_block_tmp = new ArrayList<>();
                ts_block_tmp.add(ti);
                ts_block_tmp.add(value_list.get(i));
                ts_block.add(ts_block_tmp);
            }
//            quickSort(ts_block, 0, 0, block_size - 1);
            data.addAll(ts_block);
        }

        if (remain_length == 1) {
            int timestamp_end = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            int value_end = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            ArrayList<Integer> ts_block_end = new ArrayList<>();
            ts_block_end.add(timestamp_end);
            ts_block_end.add(value_end);
            data.add(ts_block_end);
        }
        if (remain_length != 0 && remain_length != 1) {
            ArrayList<Integer> time_list = new ArrayList<>();
            ArrayList<Integer> value_list = new ArrayList<>();

            ArrayList<ArrayList<Integer>> ts_block = new ArrayList<>();

            int time0 = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            int value0 = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;

            float theta0_r = bytes2float(encoded, decode_pos);
            decode_pos += 4;
            float theta1_r = bytes2float(encoded, decode_pos);
            decode_pos += 4;
            float theta0_v = bytes2float(encoded, decode_pos);
            decode_pos += 4;
            float theta1_v = bytes2float(encoded, decode_pos);
            decode_pos += 4;

            int max_bit_width_time = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            time_list =
                    decodebitPacking(encoded, decode_pos, max_bit_width_time, 0, remain_length + zero_number);
            decode_pos += max_bit_width_time * (remain_length + zero_number - 1) / 8;

            int max_bit_width_value = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;
            value_list =
                    decodebitPacking(
                            encoded, decode_pos, max_bit_width_value, 0, remain_length + zero_number);
            decode_pos += max_bit_width_value * (remain_length + zero_number - 1) / 8;

            int td_common = bytes2Integer(encoded, decode_pos, 4);
            decode_pos += 4;

            int ti_pre = time0;
            int vi_pre = value0;
            for (int i = 0; i < remain_length + zero_number - 1; i++) {
                int ti = (int) ((double) theta1_r * ti_pre + (double) theta0_r + time_list.get(i));
                time_list.set(i, ti);
                ti_pre = ti;

                int vi = (int) ((double) theta1_v * vi_pre + (double) theta0_v + value_list.get(i));
                value_list.set(i, vi);
                vi_pre = vi;
            }

            ArrayList<Integer> ts_block_tmp0 = new ArrayList<>();
            ts_block_tmp0.add(time0);
            ts_block_tmp0.add(value0);
            ts_block.add(ts_block_tmp0);
            for (int i = 0; i < remain_length + zero_number - 1; i++) {
                int ti = (time_list.get(i) - time0) * td_common + time0;
                ArrayList<Integer> ts_block_tmp = new ArrayList<>();
                ts_block_tmp.add(ti);
                ts_block_tmp.add(value_list.get(i));
                ts_block.add(ts_block_tmp);
            }

//            quickSort(ts_block, 0, 0, remain_length + zero_number - 1);

            for (int i = zero_number; i < remain_length + zero_number; i++) {
                data.add(ts_block.get(i));
            }
        }
        return data;
    }
    public static void main(@org.jetbrains.annotations.NotNull String[] args) throws IOException {
        String parent_dir = "C:\\Users\\Jinnsjao Shawl\\Documents\\GitHub\\encoding-outlier\\";
        String output_parent_dir = parent_dir + "vldb\\compression_ratio\\outlier";


        String input_parent_dir = parent_dir + "iotdb_test_small\\";
        ArrayList<String> input_path_list = new ArrayList<>();
        ArrayList<String> output_path_list = new ArrayList<>();
        ArrayList<String> dataset_name = new ArrayList<>();
        ArrayList<Integer> dataset_block_size = new ArrayList<>();

        for (int i = 0; i < dataset_name.size(); i++) {
            input_path_list.add(input_parent_dir + dataset_name.get(i));
        }

        output_path_list.add(output_parent_dir + "\\CS-Sensors_ratio.csv"); // 0
        dataset_block_size.add(1024);
        output_path_list.add(output_parent_dir + "\\Metro-Traffic_ratio.csv");// 1
        dataset_block_size.add(512);
        output_path_list.add(output_parent_dir + "\\USGS-Earthquakes_ratio.csv");// 2
        dataset_block_size.add(512);
        output_path_list.add(output_parent_dir + "\\YZ-Electricity_ratio.csv"); // 3
        dataset_block_size.add(256);
        output_path_list.add(output_parent_dir + "\\GW-Magnetic_ratio.csv"); //4
        dataset_block_size.add(128);
        output_path_list.add(output_parent_dir + "\\TY-Fuel_ratio.csv");//5
        dataset_block_size.add(64);
        output_path_list.add(output_parent_dir + "\\Cyber-Vehicle_ratio.csv"); //6
        dataset_block_size.add(128);
        output_path_list.add(output_parent_dir + "\\Vehicle-Charge_ratio.csv");//7
        dataset_block_size.add(512);
        output_path_list.add(output_parent_dir + "\\Nifty-Stocks_ratio.csv");//8
        dataset_block_size.add(256);
        output_path_list.add(output_parent_dir + "\\TH-Climate_ratio.csv");//9
        dataset_block_size.add(512);
        output_path_list.add(output_parent_dir + "\\TY-Transport_ratio.csv");//10
        dataset_block_size.add(512);
        output_path_list.add(output_parent_dir + "\\EPM-Education_ratio.csv");//11
        dataset_block_size.add(512);


//        dataset_block_size.add(256);
        ArrayList<Integer> columnIndexes = new ArrayList<>(); // set the column indexes of compressed
        for (int i = 0; i < 2; i++) {
            columnIndexes.add(i, i);
        }

//        for (int file_i = 2; file_i < 3; file_i++) {
        for (int file_i = 0; file_i < input_path_list.size(); file_i++) {

            String inputPath = input_path_list.get(file_i);
            System.out.println(inputPath);
            String Output = output_path_list.get(file_i);

            int repeatTime = 1; // set repeat time

            File file = new File(inputPath);
            File[] tempList = file.listFiles();

            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
//                    "threshold",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head); // write header to output file

            assert tempList != null;

            for (File f : tempList) {
//                f = tempList[2];
                InputStream inputStream = Files.newInputStream(f.toPath());
                CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                ArrayList<Integer> data = new ArrayList<>();
                ArrayList<ArrayList<Integer>> data_decoded = new ArrayList<>();


                for (int index : columnIndexes) {
                    // add a column to "data"
                    //        System.out.println(index);
                    int max_precision = 0;
                    loader.readHeaders();
                    data.clear();
                    while (loader.readRecord()) {
                        String value = loader.getValues()[index];
                        data.add(Integer.valueOf(value));
                    }
                    //        System.out.println(max_precision);
                    inputStream.close();

                    long encodeTime = 0;
                    long decodeTime = 0;
                    double ratio = 0;
                    double compressed_size = 0;
                    int repeatTime2 = 1;
                    for (int i = 0; i < repeatTime; i++) {
                        long s = System.nanoTime();
                        ArrayList<Byte> buffer = new ArrayList<>();
                        long buffer_bits = 0;
                        for (int repeat = 0; repeat < repeatTime2; repeat++)
//                            buffer_bits = ReorderingRegressionEncoder(data, dataset_block_size.get(file_i), dataset_name.get(file_i));
                            buffer = ReorderingRegressionEncoder(data, dataset_block_size.get(file_i),  dataset_name.get(file_i));
                        long e = System.nanoTime();
                        encodeTime += ((e - s) / repeatTime2);
                        compressed_size += buffer_bits;
                        double ratioTmp = (double) data.size()  / (double) (data.size() * Integer.BYTES * 8);
                        ratio += ratioTmp;
                        s = System.nanoTime();
                        //          for(int repeat=0;repeat<repeatTime2;repeat++)
                        //            data_decoded = ReorderingRegressionDecoder(buffer);
                        e = System.nanoTime();
                        decodeTime += ((e - s) / repeatTime2);
                    }

                    ratio /= repeatTime;
                    compressed_size /= repeatTime;
                    encodeTime /= repeatTime;
                    decodeTime /= repeatTime;

                    String[] record = {
                            f.toString(),
                            "Windows-Reorder",
                            String.valueOf(encodeTime),
                            String.valueOf(decodeTime),
                            String.valueOf(data.size()),
                            String.valueOf(compressed_size),
                            String.valueOf(ratio)
                    };
                    writer.writeRecord(record);


                }

//

                writer.close();
            }
        }
    }

}

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

public class Rainbow {

	private static final boolean NAIVE = false;
	private static final double NAIVE_TABLE_LENGTH = Math.pow(2, 24);
	
	private static final int CHAIN_LENGTH = (int) Math.pow(2, 7.8);
	private static final int TABLE_LENGTH = (int) (Math.pow(2, 23.4) / CHAIN_LENGTH);
	private static final int SEED = 6972868;
	
	private static HashMap<String, String> table = new HashMap<>();
	private static HashMap<String, String> table2 = new HashMap<>();
	private static HashMap<String, String> inputs = new HashMap<>();
	private static HashMap<String, String> words = new HashMap<>();
	private static ArrayList<byte[]> hashesToCrack = new ArrayList<>();
	
	private static int found = 0;
	
	private static long bigT = 0;
	private static long smallT = 0;
	
	public static void main(String[] args) throws Exception {
		
		// get next word
		// build chain with this word
		// after every reduce function, check if the digest is already inside the table
		// stop if exist
		// else place (finalhash, word) pair in table.

		System.out.println("Start");
		
		timeTest();
		
		if (NAIVE) {
			buildNaive();
			readNaiveTableFromFile();
			solveNaive();
//			solveNaiveWithoutTable();
			return;
		}
		
		// step 0. test
		//testFile();
		System.out.println(toHexString(hash("20c11b")) + " vs " + "b8568363a1734335ee113002ecee05cfce857ae0");
		System.out.println(toHexString(hash("be7277")) + " vs " + "b8ab210460c74b6c2698debef13decddaf4af47e");
		System.out.println(toHexString(hash("31894b")) + " vs " + "e63b2bafaab041e3f46165ae557acb0234192d2a");
		System.out.println(toHexString(hash("500d13")) + " vs " + "ecd42f98ad2c6490fa782b800693593b0ba112a7");
		
		// step 1. build
		boolean written = false;
		if (!written) {
			build();
			System.out.println("Table built");
			writeTableToFile();
			System.out.println("Table written");
			//verify();
			//System.out.println("Table verified");
		} else {
			readTableFromFile();
			System.out.println("Table read into memory");
		}
		
		// step 2. crack
		readInputFile();
		System.out.println("Input file read");
		crack();
		System.out.println("Crack complete");
		
		// step 3. report
		System.out.println("Table size = " + table.size());
		System.out.println("Speedup = " + ((1000 * 4754.0) / smallT));
		System.out.println("Table bytes = " + ((table.size() * (160 + 24))/8000.0));
		System.out.println("End");
	}

	private static void build() throws Exception {
		Random r = new Random(SEED);
		for (int i = 0; i < TABLE_LENGTH; i++) {
			if (i % 1000 == 0) System.out.println(i);
			
			byte[] originalWord;
			String originalWordString;
			do {
				originalWord = getNextWord(r);
				originalWordString = toHexString(originalWord);
			} while (words.containsKey(originalWordString));
			words.put(originalWordString, "");
			
			byte[] word = toByteArray(originalWordString);
			byte[] hash = new byte[20];
			ArrayList<String> buffer = new ArrayList<>();
			for (int j = 0; j < CHAIN_LENGTH; j++) {
				hash = hash(word); // hash w0 -> y0
				word = reduce(hash, j); // reduce y0 -> w1
				buffer.add(toHexString(word));
			}
			String finalHashString = toHexString(hash);
			
			if (table.containsKey(finalHashString)) {
				i--;
			} else {
				table.put(finalHashString, originalWordString);
				for (String s : buffer) {
					words.put(s, "");
				}
			}
		}
		System.out.println("Number of words used: " + words.size());
		words.clear();
	}
	
	private static void readInputFile() throws Exception {
		//BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt"));
		Scanner sc = new Scanner(new BufferedInputStream(new FileInputStream("SAMPLE_INPUT.data")));
		for (int i = 0; i < 1000; i++) { // for each given hash
			
			// convert given hex string into byte[]
			ByteBuffer bf = ByteBuffer.allocate(20);
			for (int j = 0; j < 5; j++) {
				bf.putInt((int)sc.nextLong(16));
			}
			byte[] inputHash = bf.array();
			hashesToCrack.add(inputHash);
		}
		//bw.close();
		sc.close();
	}
	
	private static void crack() throws Exception {
		long startTime = System.currentTimeMillis();
		BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt"));
		for (byte[] inputHash : hashesToCrack) { // for each given hash
			
			byte[] resultWord;
			boolean got = false;
			for (int j = CHAIN_LENGTH - 1, j2 = 0; j >= 0; j--, j2++) {
				byte[] word;
				byte[] hash = Arrays.copyOf(inputHash, inputHash.length); // hashed w0 -> y0
				for (int k = j; k < CHAIN_LENGTH - 1; k++) {
					word = reduce(hash, k); // reduce yk -> wk+1
					hash = hash(word); // hash wx -> yx
				}
				String hashString = toHexString(hash);
				if (table.containsKey(hashString)) {
					resultWord = getPreimage(table.get(hashString), inputHash, j2+1);
					if (!Arrays.equals(resultWord, new byte[1])) {
						found++;
						got = true;
						String resultWordString = toHexString(resultWord);
						bw.write(resultWordString);
						bw.write("\n");
						break;
					}
				}
			}
			if (!got) {
				bw.write("0\n");
			}
		}
		System.out.println("Words found = " + found);
		bw.close();
		//sc.close();
		
		long endTime = System.currentTimeMillis();
		long timeTaken = ((endTime - startTime));
		smallT = timeTaken;
		System.out.println("Time taken for crack: " + timeTaken + " milliseconds");
	}
	
	private static byte[] getPreimage(String startWordString, byte[] targetHash, int numTimes) throws Exception {
		// get the preimage of hash by chaining until hash
		byte[] word = toByteArray(startWordString);
		byte[] hash = new byte[20];
		hash = hash(word);
		for (int j = 0; j < CHAIN_LENGTH; j++) {
			if (Arrays.equals(hash, targetHash)) {
				return word;
			}
			word = reduce(hash, j);
			hash = hash(word);
		}
		return new byte[1];
	}
	
	private static byte[] hash(String wordString) throws Exception {
		byte[] word = toByteArray(wordString);
		return hash(word);
	}
	
	private static byte[] hash(byte[] word) throws Exception {
		MessageDigest sha1 = MessageDigest.getInstance("SHA1");
		return sha1.digest(word);
	}
	
	private static byte[] reduce(byte[] hash, int iteration) {
		int start = iteration % 17;
		byte[] reduced = Arrays.copyOfRange(hash, start, start + 3);
		reduced[0] += (71 * iteration) % 251;
		reduced[1] += (107 * iteration) % 251;
		reduced[2] += (197 * iteration) % 251;
		return reduced;
	}
	
	private static byte[] reduce2(byte[] hash, int iteration) {
		IntBuffer intBuf = ByteBuffer.wrap(hash).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
		int[] d = new int[intBuf.remaining()];
		intBuf.get(d);

		byte[] reduced = new byte[3];
		reduced[0] = (byte)((d[0]+iteration*7) % 256);
		reduced[1] = (byte)((d[1]) % 256);
		reduced[2] = (byte)((d[2]) % 256);
		
		return reduced;
	}
	
	private static byte[] reduce3(byte[] hash, int iteration) {
		byte last_byte = (byte) iteration;
        byte[] word = new byte[3];
        for (int i = 0; i < word.length; i++) {
            word[i] = (byte) (hash[(iteration + i) % 20] + last_byte);
        }
        return word;
	}
	
	private static String toHexString(byte[] barr) {
		StringBuffer sb= new StringBuffer();
		for (int i = 0; i < barr.length; i++) {
            sb.append(Integer.toString((barr[i] & 0xff) + 0x100, 16).substring(1));
        }
		return sb.toString();
//		HexBinaryAdapter adapter = new HexBinaryAdapter();
//        String str = adapter.marshal(barr);
//        return str;
	}
	
	private static byte[] getNextWord(Random r) {
		byte[] nextWord = new byte[3];
		r.nextBytes(nextWord);
		return nextWord;
	}
	
	private static byte[] getByteArray(int val, int size) {
		ByteBuffer bf = ByteBuffer.allocate(4);
		bf.putInt(val);
		return Arrays.copyOfRange(bf.array(), 1, 4);
	}
	
	private static void writeTableToFile() throws Exception {
		BufferedWriter bw = new BufferedWriter(new FileWriter("table.data"));
		for (Map.Entry<String, String> e : table.entrySet()) {
			bw.write(e.getKey());
			bw.write(" ");
			bw.write(e.getValue());
			bw.write("\n");
		}
		bw.flush();
		bw.close();
	}

	private static byte[] toByteArray(String startWordString) {
		ByteBuffer bf = ByteBuffer.allocate(4);
		Scanner sc = new Scanner(startWordString);
		bf.putInt(sc.nextInt(16));
		sc.close();
		return Arrays.copyOfRange(bf.array(), 1, 4);
	}
	
	private static void testFile() throws Exception {
		Scanner sc = new Scanner(new FileInputStream("SAMPLE_INPUT.data"));
		for (int i = 0; i < 1000; i++) { // for each given hash
			
			ByteBuffer bf = ByteBuffer.allocate(20);
			for (int j = 0; j < 5; j++) {
				bf.putInt((int)sc.nextLong(16));
			}
			
			byte[] inputHash = bf.array();
			String inputHashString = toHexString(inputHash);
			inputs.put(inputHashString, "");
			System.out.println(inputHashString);
		}
		sc.close();
	}
	
	private static boolean verify() throws Exception {
		int failCount = 0;
		for (Map.Entry<String, String> e : table.entrySet()) {
			byte[] word = toByteArray(e.getValue());
			byte[] hash = new byte[20];
			for (int j = 0; j < CHAIN_LENGTH; j++) {
				hash = hash(word);
				word = reduce(hash, j);
			}
			if (!e.getKey().equals(toHexString(hash))) {
				failCount++;
				System.out.println("failed! " + e.getValue() + " does not chain to " + e.getKey());
			}
			
			byte[] firstWordHash = hash(e.getValue());
			byte[] preimage = getPreimage(e.getValue(), (firstWordHash), 0);
			if (!e.getValue().equals(toHexString(preimage))) {
				System.out.println("failed! " + e.getValue() + " can't be gotten from " + toHexString(firstWordHash));
			}
			byte[] secondWord = reduce(firstWordHash, 0);
			byte[] secondWordHash = hash(secondWord);
			byte[] preimage2 = getPreimage(e.getValue(), (secondWordHash), 0);
			if (!toHexString(secondWord).equals(toHexString(preimage2))) {
				System.out.println("failed! " + toHexString(secondWord) + " can't be gotten from " + toHexString(secondWordHash));
			}
		}
		System.out.println(failCount + " out of " + table.size() + " in the table failed");
		return failCount > 0;
	}
	
	private static void timeTest() throws Exception {
		long startTime = System.currentTimeMillis();
		MessageDigest sha1 = MessageDigest.getInstance("SHA1");
		for (int i = 0; i < (1024*1024*8); i++) {
			byte[] b = {(byte) i, (byte) i, (byte) i};
			sha1.digest(b);
		}
		long endTime = System.currentTimeMillis();
		long timeTaken = ((endTime - startTime));
		bigT = timeTaken;
		System.out.println("Time taken for timeTest: " + timeTaken + " milliseconds");
	}
	
	private static void buildNaive() throws Exception {
		BufferedWriter bw = new BufferedWriter(new FileWriter("complete-table.data"));
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < NAIVE_TABLE_LENGTH; i++) {
			if (i % 1000000 == 0) System.out.println(i);
			byte[] originalWord = getByteArray(i, 3);
			String originalWordString = toHexString(originalWord);
			byte[] finalHash = hash(originalWord);
			String finalHashString = toHexString(finalHash);
			bw.write(finalHashString + " " + originalWordString + "\n");
		}
		long endTime = System.currentTimeMillis();
		long timeTakenSeconds = ((endTime - startTime) / 1000);
		System.out.println("Time taken for naive: " + timeTakenSeconds + " seconds");
		bw.close();
	}
	
	private static void readTableFromFile() throws Exception {
		Scanner sc = new Scanner(new BufferedInputStream(new FileInputStream("table.data")));
		System.out.println(TABLE_LENGTH);
		while (sc.hasNext()) {
			String finalHashString = sc.next();
			String originalWordString = sc.next();
			table.put(finalHashString, originalWordString);
		}
		System.out.println("File read. Table size: " + (table.size() + table2.size()));
		sc.close();
	}
	
	private static void readNaiveTableFromFile() throws Exception {
		Scanner sc = new Scanner(new BufferedInputStream(new FileInputStream("complete-table.data")));
		System.out.println(NAIVE_TABLE_LENGTH/2);
		for (int i = 0; i < ((int)NAIVE_TABLE_LENGTH/2); i++) {
			if (i % 1000000 == 0) System.out.println(i);
			String finalHashString = sc.next();
			String originalWordString = sc.next();
			table.put(finalHashString, originalWordString);
		}
		System.out.println("next table");
		for (int i = (int) (NAIVE_TABLE_LENGTH/2); i < NAIVE_TABLE_LENGTH; i++) {
			if (i % 1000000 == 0) System.out.println(i);
			String finalHashString = sc.next();
			String originalWordString = sc.next();
			table2.put(finalHashString, originalWordString);
		}
		System.out.println("File read. Table size: " + (table.size() + table2.size()));
		sc.close();
	}
	
	private static void solveNaive() throws Exception {
		BufferedWriter bw = new BufferedWriter(new FileWriter("naive-result.txt"));
		Scanner sc = new Scanner(new FileInputStream("SAMPLE_INPUT.data"));
		int wordsFound = 0;
		for (int i = 0; i < 1000; i++) { // for each given hash
			
			ByteBuffer bf = ByteBuffer.allocate(20);
			for (int j = 0; j < 5; j++) {
				bf.putInt((int)sc.nextLong(16));
			}
			
			byte[] inputHash = bf.array();
			String inputHashString = toHexString(inputHash);
			if (table.containsKey(inputHashString)) {
				wordsFound++;
				bw.write(table.get(inputHashString) + "\n");
			} else if (table2.containsKey(inputHashString)) {
				wordsFound++;
				bw.write(table2.get(inputHashString) + "\n");
			} else {
				bw.write("0\n");
			}
		}
		bw.write("Total Words Found: " + wordsFound);
		sc.close();
		bw.close();
	}
	
	private static void solveNaiveWithoutTable() throws Exception {
		BufferedWriter bw = new BufferedWriter(new FileWriter("naive-result.txt"));
		Scanner sc = new Scanner(new FileInputStream("SAMPLE_INPUT.data"));
		int wordsFound = 0;
		for (int i = 0; i < 1000; i++) { // for each given hash
			System.out.println("word number " + i);
			ByteBuffer bf = ByteBuffer.allocate(20);
			for (int j = 0; j < 5; j++) {
				bf.putInt((int)sc.nextLong(16));
			}
			
			byte[] inputHash = bf.array();
			String inputHashString = toHexString(inputHash);
			boolean got = false;
			Scanner scTable = new Scanner(new FileInputStream("complete-table.data"));
			for (int j = 0; j < NAIVE_TABLE_LENGTH; j++) {
				if (j % 1000000 == 0) System.out.println(j);
				String[] hashWordPair = scTable.nextLine().split("\\s+");
				if (hashWordPair[0].equals(inputHashString)) {
					wordsFound++;
					bw.write(hashWordPair[1] + "\n");
					bw.flush();
					got = true;
					break;
				}
			}
			if (!got) {
				bw.write("0\n");
				bw.flush();
			}
			scTable.close();
		}
		bw.write("Total Words Found: " + wordsFound);
		sc.close();
		bw.close();
	}

}

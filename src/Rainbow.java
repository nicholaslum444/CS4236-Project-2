import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Rainbow {
	
	private static final int CHAIN_LENGTH = (int) Math.pow(2, 8);
	private static final int TABLE_LENGTH = (int) (Math.pow(2, 24) / CHAIN_LENGTH);
	private static final int SEED = 444;
	
	private static HashMap<String, String> table = new HashMap<>();
	
	public static void main(String[] args) throws Exception {
		
		/*BufferedReader br = new BufferedReader(new FileReader("SAMPLE_INPUT.data"));
		
		String line = br.readLine();
		while (line != null) {
			String[] lineArr = line.split("\\s+");
			lineArr = Arrays.copyOfRange(lineArr, 1, lineArr.length);
			System.out.println(Arrays.toString(lineArr));
			
			line = br.readLine();
		}*/
		
		// digest is 20 bytes long (160 bits)
		
//		MessageDigest sha1 = MessageDigest.getInstance("SHA1");
//		System.out.println(sha1.getAlgorithm());
//		byte[] result = sha1.digest("abc".getBytes());
//        StringBuffer sb = new StringBuffer();
//		for (int i = 0; i < result.length; i++) {
//            sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
//        }
//		System.out.println(sb);
		// verified that the java sha1 can encode abc properly


		// use scanner to read in the next long as base 16
		// this is for reading the digest in the input file
//		Scanner sc = new Scanner(System.in);
//		ByteBuffer bf = ByteBuffer.allocate(20);
//		for (int i = 0; i < 5; i++) {
//			//bf.putInt((int)sc.nextLong(16));
//		}
		
//		byte[] barr = bf.array();
//		System.out.println(Arrays.toString(barr));
//		sb = new StringBuffer();
//		for (int i = 0; i < barr.length; i++) {
//            sb.append(Integer.toString((barr[i] & 0xff) + 0x100, 16).substring(1));
//        }
//		// outputs the read file line as a byte array. 
//		// it matches. hence it can read properly
//		System.out.println(sb);
//		// verified that it can read the input file properly
//		
//		System.out.println("done");
//		sc.close();
		
		
		
		// get next word
		// build chain with this word
		// after every reduce function, check if the digest is already inside the table
		// stop if exist
		// else place (finalhash, word) pair in table.
		
		System.out.println("Start");
		// step 1. build
		build();
		System.out.println("Table built");
		writeTableToFile();
		System.out.println("Table written");
		//buildNaive();
		// step 2. crack
		crack();
		System.out.println("Crack complete");
		System.out.println("End");
	}
	
	private static void build() throws Exception {
		Random r = new Random(SEED);
		for (int i = 0; i < TABLE_LENGTH; i++) {
//			System.out.println(i);
			if (i % 1000 == 0) System.out.println(i);
			
			byte[] originalWord = getNextWord(r);
			
			//System.out.println("This is the original word for " + i + " " + Arrays.toString(originalWord));
			
			byte[] word = Arrays.copyOf(originalWord, 3);
			byte[] hash = new byte[20];
			for (int j = 0; j < CHAIN_LENGTH; j++) {
				hash = hash(word);
				//System.out.println(Arrays.toString(hash));
				word = reduce(hash, j);
				//System.out.println(Arrays.toString(word));
			}
			
			String finalHashString = toHexString(hash);
			String originalWordString = toHexString(originalWord);
//			System.out.println("This is the final hash for " + i + " " + finalHashString);
//			System.out.println("This is the original word for " + i + " " + originalWordString);
//			System.out.println("table size: " + table.size());
			if (table.containsKey(finalHashString)) {
				//System.out.println(i + " inside alr");
			} else {
//				System.out.println(i + " not inside");
				table.put(finalHashString, originalWordString);
			}
		}
		//System.out.println(table.toString());
	}
	
	private static void crack() throws Exception {
		Scanner sc = new Scanner(System.in);
		int found = 0;
		for (int i = 0; i < 1000; i++) {
			
			ByteBuffer bf = ByteBuffer.allocate(20);
			for (int j = 0; j < 5; j++) {
				bf.putInt((int)sc.nextLong(16));
			}
			
			byte[] inputHash = bf.array();
			String inputHashString = toHexString(inputHash);
			//System.out.println(inputHashString);
			
			if (table.containsKey(inputHashString)) {
				found++;
				System.out.println("contains");
			} else {
				byte[] word;
				byte[] hash = Arrays.copyOf(inputHash, 20);
				for (int j = CHAIN_LENGTH - 1; j >= 0; j--) {
					for (int k = j; k < CHAIN_LENGTH; k++) {
						word = reduce(hash, k);
						hash = hash(word);
					}
					String hashString = toHexString(hash);
					if (table.containsKey(hashString)) {
						found++;
						//System.out.println("contains");
						// construct the chain until preimage
						byte[] preimage = getPreimage(hash);
						break;
					}
				}
			}
		}
		System.out.println(found);
		
		sc.close();
	}
	
	private static byte[] hash(byte[] word) throws Exception {
		MessageDigest sha1 = MessageDigest.getInstance("SHA1");
		return sha1.digest(word);
	}
	
	private static byte[] reduce(byte[] hash, int iteration) {
		int start = iteration % 17;
		byte[] reduced = Arrays.copyOfRange(hash, start, start + 3);
		for (int i = 0; i < reduced.length; i++) {
			reduced[0] += iteration;
		}
		
		return reduced;
	}
	
	private static String toHexString(byte[] barr) {
		StringBuffer sb= new StringBuffer();
		for (int i = 0; i < barr.length; i++) {
            sb.append(Integer.toString((barr[i] & 0xff) + 0x100, 16).substring(1));
        }
		return sb.toString();
	}
	
	private static byte[] getNextWord(Random r) {
		byte[] nextWord = new byte[3];
		r.nextBytes(nextWord);
		return nextWord;
	}
	
	private static void buildNaive() throws Exception {
		for (int i = 0; i < TABLE_LENGTH; i++) {
			if (i % 1000 == 0) System.out.println(i);
			byte[] originalWord = getByteArray(i, 3);
			String originalWordString = toHexString(originalWord);
			//System.out.println(originalWordString);
			byte[] finalHash = hash(originalWord);
			String finalHashString = toHexString(finalHash);
			table.put(finalHashString, originalWordString);
		}
	}
	
	private static byte[] getByteArray(int val, int size) {
		ByteBuffer bf = ByteBuffer.allocate(4);
		bf.putInt(val);
		return Arrays.copyOfRange(bf.array(), 1, 4);
	}
	
	private static void writeTableToFile() throws Exception {
		BufferedWriter bw = new BufferedWriter(new FileWriter("table.data"));
		for (Map.Entry<String, String> e : table.entrySet()) {
			StringBuilder sb = new StringBuilder();
			sb.append(e.getKey());
			sb.append(" ");
			sb.append(e.getValue());
			sb.append("\n");
			bw.write(sb.toString());
		}
		bw.flush();
		bw.close();
	}
	
	private static byte[] getPreimage(byte[] hash) {
		
		return null;
	}

}

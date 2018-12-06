import java.util.*;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in - Buffered bit stream of the file to be compressed.
	 * @param out - Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
		
	}
	
	/**
	 * Creates the array of frequencies by reading the bits
	 * @param in
	 * @return
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		
		while(true) {
			int val = in.readBits(BITS_PER_INT);
			if(val == -1) 
				break;
			freq[val] += 1;
			freq[PSEUDO_EOF] = 1;
		}
		
		return freq;
	}
	
	/**
	 * Uses a priority queue to construct a tree from the previously created 
	 * array of frequencies. 
	 * @param freq - array of frequencies 
	 * @return
	 */
	private HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i = 0; i < freq.length; i++) {
			if(freq[i] > 0) {
				pq.add(new HuffNode(i, freq[i], null, null));
			}
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
		
		return root;
	}
	
	/**
	 * Creates the encodings. Calls the recursive helper method to do this
	 * @param root
	 * @return
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}
	
	/**
	 * Similar to the LeafTrails APT. Creates a path based on the previously 
	 * designed tree by adding 0's and 1's. Uses recursion to complete this path
	 * @param root
	 * @return
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root == null) {  return;  }
		
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			if(myDebugLevel >= DEBUG_HIGH) {
				System.out.println("encoding for " + root.myValue + " is " + path);
			}
			return;
		}
		
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
		
	}

	/**
	 * Recursive method to write the tree. Calls itself if the value is not a 
	 * leaf (pre order traversal)
	 * @param root
	 * @param out
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft != null && root.myRight != null) { //internal node
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
	}
	
	/**
	 * Writes the encoded bits to the output. This method uses the encodings to 
	 * write the bits, and writes a PSEUDO_EOF character at the end. 
	 * @param encoding
	 * @param in
	 * @param out
	 */
	private void writeCompressedBits(String[] encoding, BitInputStream in, BitOutputStream out) {
		while(true) {
			int val = in.readBits(BITS_PER_INT);
			if(val == -1)
				break;
			String code = encoding[val];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		String code = encoding[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in - Buffered bit stream of the file to be decompressed.
	 * @param out - Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	/**
	 * This method reads the tree header and throws an exception if needed. This
	 * method is recursive, and uses a pre-order traversal. 
	 * @param in
	 * @return
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if(bit == -1) {
			throw new HuffException("reading bits failed");
		}
		if(bit == 0) { //an internal node --> recursion
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		else {
			int val = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(val, 0, null, null);
		}
	}
	
	/**
	 * This method reads the compressed bits, and throws an exception if needed.
	 * This method goes through the tree and writes the value of the node (once 
	 * it reaches a leaf) to the output. 
	 * @param root
	 * @param in
	 * @param out
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits == -1) {
				throw new HuffException("bad input");
			} 
			
			else {
				if(bits == 0) {  current = current.myLeft;  } //read a 0, go left
				else 		  {  current = current.myRight; } //read a 1, go right
				
				if(current.myLeft == null && current.myRight == null) { //at leaf
					
					if(current.myValue == PSEUDO_EOF) {   break;  } //end of file
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; //start again
					}
				}
			}
		}
	}
}


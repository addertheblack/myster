package com.myster.mml;

import java.util.Vector;
import java.io.*;
	
public class MML implements Serializable {
	
	public MML() {
		startNode=new RootNode();
	}
	
	public MML(String s) throws MMLException{
		if (s==null) throw new NullPointerException("Argument is null");
		try {
			startNode=createBranch(s, null);
		} catch (Exception ex) {
			throw new MMLException("String is not an MML string");
		}
		//System.out.println("THingy is still null?"+startNode);//if (startNode==null) startNode=new RootNode();
	}
	
	public MML(MML mml) {
		startNode=mml.copyMML().startNode; //now both objects point to a copy of the same structure.
	}
	
	final protected Branch startNode;
	//private MML mml;
	/*
	private void createMML() {
		/*
		try {
		
			File file =new File("mysterprefs.mml");
			String s=loadAsBytes(file);
			
			calculateMemoryUsage(s);
			calculateMemoryUsage2(s);
			
			long starttime=System.currentTimeMillis();
			for (int i=0; i<20; i++)
				mml=new MML(s);//startNode=createBranch(s, null);
			System.out.println("TIme="+(System.currentTimeMillis()-starttime));
			mml=new MML(s);
			startNode=new RootNode();
			/*addLeaf(startNode, "/", new Leaf("tag", "Data"));
			addBranch(startNode, "/", new Branch("Photos"));
			addBranch(startNode, "/Photos/", new Branch("Today"));
			addBranch(startNode, "/Photos/", new Branch("Yesterday's"));
			addLeaf(startNode, "/Photos/Today/", new Leaf("My Foot", "(A picture of my foot)"));
			addLeaf(startNode, "/Photos/Yesterday's/", new Leaf("My Arm", "mooo"));
			put(startNode, "/Photos/Yesterday's/carrot/mommy/zerg", "google");
			put(startNode, "/Photos/Yesterday's/carrot/mommy/poo", "google");
			remove(startNode, "/Photos/Yesterday's/carrot/mommy/poo");
			remove(startNode, "/Photos/Yesterday's/carrot/mommy/zerg");
			
			String path1="/rat/cat/fat/nat/sprat/zap";
			String path2="/rat/zat";
			String path3="/rat/cow";
			put(startNode, path1, "mittens");
			put(startNode, path2, "mittens");
			put(startNode, path3, "bob");
			System.out.println(get(startNode, path1));
			System.out.println(get(startNode, "/fuck/rat/cat/fat/nat/sprat/zap"));
			remove(startNode, "/fuck/rat/cat/fat/nat/sprat/fdgdsfgdsfg/");//path2);
			//put(startNode, "/Photos/Yesterday's", "google");
			//addLeaf(startNode, "/Photos/Yesterday's/", new Leaf("My Arm", ""));
			//addLeaf(startNode, "/tag/futter//", new Leaf("ha", "not going to work"));
			//addLeaf(startNode, "/bugger/", new Leaf("ha", "not going to work"));
			//addLeaf(startNode, "/Photos//", new Leaf("ha", "not going to work"));
			//addLeaf(startNode, "/Photos/Yesterday's/My Arm/", new Leaf("My Arm", "mooo"));
			//assertBranch(startNode, "/Photos/Yesterday's/My Arm/wibble/nuke/dung//");
			//assertBranch(startNode, "/Photos/Yesterday's/caca/nuke/dung/");
			//deleteLeaf(startNode, "/Photos/Yesterday's/caca/wibble/nuke/");
			//clearBranch(startNode, "/Photos/Yesterday's/");
			Vector vector=getTagListing(startNode, "/rat/");
			for (int i=0; i<vector.size(); i++) {
				System.out.println("/rat/"+vector.elementAt(i).toString());
			}
			
			System.out.print(toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
	}
	*/
	/**
		Adds a value for the key path. If the path doesn't exist it is created. Always returns true or throws an execption.
	*/
	public synchronized boolean put(String path, String value) { //tried to add value to branch, syntax error (bad path)
		try {
			put(startNode, path, value);
			return true;
		} catch (NonExistantPathException ex) {
			return false;
		}
		/*
		return false; //not reached.
		*/
	}
	
	/**
		Removes the value at key path. All empty branch nodes along the path are deleted.
		Returns the value at key path. If path is invalid does not delete anything and returns null.
	*/
	public synchronized String remove(String path) {
		try {
			return MML.remove(startNode, path);
		} catch (NonExistantPathException ex) {
			return null;
		}
	}
	
	/**
		Gets the value at key path. If path doens't exoist, returns null.
	*/
	public synchronized String get(String path) {
		try {
			return MML.get(startNode, path);
		} catch (NonExistantPathException ex) {
			return null;
		}
	}
	
	public synchronized String query(String path) {
		String s_temp=get(path);
		if (s_temp==null) return "";
		return s_temp;
	}
	
	/**
		gets a listing at path. if path is bad returns null.
	*/
	public synchronized Vector list(String path) {
		return getTagListing(startNode, path);
	}
	
	
	/**
		returns true if path points to a value. false otherwise.
	*/
	public synchronized boolean isAValue(String path) {
		try {
			return isALeaf(getNode(startNode, path));
		} catch (NonExistantPathException ex) {
			return false;
		}
	}
	
	public synchronized boolean isAFile(String path) {
		return isAValue(path);
	}
	
	/**
		...
	*/
	public synchronized boolean isADirectory(String path) {
		try {
			return isABranch(getNode(startNode, path));
		} catch (NonExistantPathException ex) {
			return false;
		}
	}
	
	public synchronized void removeDirectoryContents(String path) {
		clearBranch(startNode, path);
	}
	
	public synchronized void removeDirectory(String path) {
		deleteBranch(startNode, path);
	}
	
	public synchronized void removeFile(String path) {
		deleteBranch(startNode, path);
	}
	
	public synchronized boolean pathExists(String path) {
		return pathExists(startNode, path);
	}
	
	
	public synchronized MML copyMML() {
		try {
			return new MML(this.toString());
		} catch (Exception ex) {
			throw new Error("A serious programming error in copyMML()");
		}
	}
	/*
	public synchronized MML copyMML(String path) {
		String sectionString;
		try {
			sectionString = makeString(getBranch(startNode, path));
		} catch (NonExistantPathException ex) {
			return null;
		}
		
		try {
			return new MML(sectionString);
		} catch (Exception ex) {
			throw new Error("A serious programming error in copyMML(String)");
		}
	}
	*/
	/*
    private void calculateMemoryUsage(String s) throws Exception {
      mml=new MML(s);
      //startNode=createBranch(s, null);
      long mem0 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      long mem1 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      mml=null;//startNode = null;
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      mem0 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      mml=new MML(s);//startNode=createBranch(s, null);;
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      mem1 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      System.out.println("Memeory used for the fat version is: "+( mem1 - mem0)+"bytes");
     }
     
    private void calculateMemoryUsage2(String s) throws Exception {
     	startNode=createBranch(s, null);
      long mem0 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      long mem1 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      startNode = null;
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      mem0 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      startNode=createBranch(s, null);
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      System.gc(); System.gc(); System.gc(); System.gc();
      mem1 = Runtime.getRuntime().totalMemory() -
        Runtime.getRuntime().freeMemory();
      System.out.println("Memeory used for the slim version is: "+( mem1 - mem0)+"bytes");
     }
     */
     
	/*
	private synchronized String loadAsBytes(File f) throws Exception {
		DataInputStream in=new DataInputStream(new FileInputStream(f));
		
		String working=in.readUTF();
		if (working.equals("This file is in bytes not a UTF")) {
			int size=in.readInt();
			byte[] temp=new byte[size];
			in.readFully(temp);
			return new String(temp);
		} else {
			System.out.println("Reading in older Myster DEV 9.5 and earlyer style preferences...");
			return working;
		}
	}
	*/
	
	/**
		paths with "//" in them are errors. Paths that start with a char other than "/" are errors.
	*/
	protected static Node getNode(Node node, PathVector pathVector, int index) throws NonExistantPathException {
		if (node==null) return null;//?

		String workingTag=pathVector.getToken(index);
		boolean hasMore=pathVector.hasMore(index);
		
		if (!hasMore) { //endgame
			if (workingTag.equals("")) {
				if (!(node instanceof Branch)) throw new LeafAsABranchException("?");
				return (Branch)node; //..!
			} else {
				Node tempnode=Link.getNode(((Branch)node).head, workingTag);
				if (tempnode==null) throw new NonExistantPathException("?");
				if (!(tempnode instanceof Leaf)) throw new BranchAsALeafException("?");
				return tempnode;
			}
		} else {
			Node tempnode=(Link.getNode(((Branch)node).head, workingTag));
			if (tempnode==null) throw new NonExistantPathException("?");
			if (!(tempnode instanceof Branch)) throw new LeafAsABranchException("?");
			
			return getNode((Branch)(tempnode), pathVector, index+1); 
		}
	}
	
	protected static Node getNode(Node node, String path) throws NonExistantPathException {
			try {
				return getNode(node, parsePath(path), 0); 
			} catch (LeafAsABranchException ex) { //slow, recursive exception handling.
				throw new LeafAsABranchException(path);
			} catch (BranchAsALeafException ex) { //slow, recursive exception handling.
				throw new BranchAsALeafException(path);
			} catch (NonExistantPathException ex) { //slow, recursive exception handling.
				throw new NonExistantPathException(path);
			}
	}
	
	protected static Branch getBranch(Branch b, String path) throws NonExistantPathException {
		return (Branch)getNode(b, path);
	}
	
	protected static Leaf getLeaf(Branch b, String path) throws NonExistantPathException {
		return (Leaf)getNode(b, path);
	}
	
	protected static Vector getTagListing(Branch b, String path)  {
		Vector vector=new Vector(10,1000);
		
		Branch branchToList;
		try {
			branchToList=getBranch(b,path); //will toss a improper cast thing if it goes wrong.
		} catch (NonExistantPathException ex) {
			return null;
		}
		if (branchToList==null) return null; //bad path
		
		Link.list(branchToList.head, vector);
		
		vector.trimToSize();
		return vector;
	}
	
	protected static void put(Branch root, String path, String value) throws NonExistantPathException  {
		PathVector vector=parsePath(path);
		if (vector.isLeafPath()) {
			String newPath="/";
			int size=(vector.size()-1);
			for (int i=0; i<size;i++) {
				newPath=newPath+vector.getToken(i)+"/";
			}
			String tag=vector.getToken(size);
			
			Leaf newLeaf=new Leaf(tag, value);
			assertBranch(root, newPath);
			try {
				addLeaf(root, newPath, newLeaf);
			} catch (NodeAlreadyExistsException ex) {
				Node n=getNode(root, path);
				deleteLeaf(root, path);
				try {
					addLeaf(root, newPath, newLeaf);
				}  catch (NodeAlreadyExistsException exp) {
					throw new RuntimeException("Invalid *assumption* in put. Node already exists");
				}
			}
		} else {
			throw new BranchAsALeafException(path);
		}
	}
	
	protected static String get(Branch root, String path) throws NonExistantPathException {
		Leaf n_temp=(Leaf)(getLeaf(root, path));
		if (n_temp==null) return null;
		return (String)(n_temp.value);
	}
	
	protected static String remove(Branch root, String path) throws NonExistantPathException {
		String value;
		try {
			Leaf leaf=null;
			try {
				leaf=(Leaf)(getNode(root, path));
			} catch (MMLPathException ex) {
				throw new MMLPathException("Bad path: "+path);
			}
			value=leaf.value;
			PathVector vector=parsePath(path);
			deleteLeaf(root, path);
			int size=(vector.size()-1);
			for (int i=0; i<size; i++) {
				String newPath="/";
				for (int j=0; j<size-i;j++) {
					newPath=newPath+vector.getToken(j)+"/";
				}
				Branch workingBranch=(Branch)(getNode(root, newPath));
				if (workingBranch.head.next==null) deleteBranch(root,newPath);
				else break;
			}
		} catch (MMLPathException ex) {
			ex.printStackTrace();
			return null;
		}
		return value;
	}
	
	protected static void addBranch(Branch root, String path, Branch toAdd) throws MMLPathException,NoStartingSlashException, NonExistantPathException,NodeAlreadyExistsException {
		addNode(root, path, toAdd);
	}
	
	protected static void addLeaf(Branch root, String path, Leaf toAdd) throws MMLPathException,NoStartingSlashException, NonExistantPathException, NodeAlreadyExistsException {
		addNode(root, path, toAdd);
	}
	
	private static void addNode(Branch root, String path, Node toAdd) throws MMLPathException,NoStartingSlashException, NonExistantPathException,NodeAlreadyExistsException {
		Branch branch=getBranch(root, path);
		
		if (Link.getNode(branch.head, toAdd.tag)!=null) throw new NodeAlreadyExistsException(toAdd.tag+" at "+path);;
		
		Link.addLink(branch.head, new Link(toAdd));
	}
	
	protected static void deleteBranch(Branch root, String path)  {
		try {
			deleteNode(root,path);
		} catch (NonExistantPathException ex) {
			throw new MMLPathException(path+" does not exist.");
		}
	}
	
	protected static void deleteLeaf(Branch root, String path) throws NonExistantPathException {
		try {
			deleteNode(root,path);
		} catch (NonExistantPathException ex) {
			throw new MMLPathException(path+" does not exist.");
		}
	}
	
	protected static void deleteNode(Branch root, String path) throws NonExistantPathException {
		Node node=getNode(root, path);
		
		PathVector pathVector=parsePath(path);
		String newPath="/";
		int size=(pathVector.isBranchPath()?pathVector.size()-2:pathVector.size()-1);
		if (size<0) throw new MMLPathException("Cannot delete root path.");
		for (int i=0; i<size;i++) {
			newPath=newPath+pathVector.getToken(i)+"/";
		}
		String tag=pathVector.getToken(size);
		//System.out.println("Tag:"+newPath);
		Link.removeLink(((Branch)(getNode(root, newPath))).head, tag); //note, no cast stuff required here.. MUST be a branch class.
	}
	
	protected static void clearBranch(Branch root, String path) {
		try {
			Branch oldBranch=getBranch(root, path);
			oldBranch.head.next=null; //gotta love code like this. Go gc go!
		} catch (NonExistantPathException ex) {
			throw new MMLPathException(path+" does not exist"); //moron!
		}
	}
	
	//If it was not there it is now. The end of an init path is ALWAYS a branch.
	protected static void assertBranch(Branch root, String path) {
		PathVector vector=parsePath(path);
		if (!(vector.size()>=1&&vector.isBranchPath())) throw new MMLPathException("Cannot Assert a leaf (path doesn't end with a '/') :"+path);
		int i;
		Branch working=root;
		for (i=0; i<vector.size()-1; i++) {
			String temp=vector.getToken(i);
			if (temp.equals("")) throw new RuntimeException("Invalid Assumption in assert branch. '' detected!");
			
			Branch tempBranch;
			try {
				tempBranch=(Branch)(Link.getNode(working.head, temp));
			} catch (ClassCastException ex) {
				throw new LeafAsABranchException("Came accross a leaf \""+temp+"\" in "+path);
			}
			
			if (tempBranch==null) {
				try {
					tempBranch=new Branch(temp);
					addBranch(working, "/", tempBranch); 
				} catch (NodeAlreadyExistsException ex) {
					throw new RuntimeException ("Invalid Assumption in assert Branch. Node Already Exists");
				} catch (NonExistantPathException ex) {
					throw new RuntimeException ("Invalid Assumption in assert Branch: "+ex.toString());
				}
			}
			working=tempBranch;
		}
		//if (i==0) throw new RuntimeException ("Invalid Assumption in assert Branch. ParsePath did not handle \"\", null paths and none '/' starting paths: "+path);
	}
	
	protected static boolean pathExists(Branch root, String path) {
		try {
			Node node=getNode(root, path);
			if (node==null) throw new NullPointerException(); //This shouldn't happen
			return true;
		} catch (NonExistantPathException ex) {
			return false;
		}
	
	}
	
	protected static boolean isABranch(Node node) {
		return (node instanceof Branch);
	}
	
	protected static boolean isALeaf(Node node) {
		return (node instanceof Leaf);
	}
	
	protected static String getNextName(String workingString) throws MMLPathException { //
		if (workingString.length()==0) throw new MMLPathException("Bad path Exception 1");
		if (workingString.length()==1) {
			if (workingString.charAt(0)=='/') return "";
			else throw new MMLPathException("Bad path Exception 2");
		}
	
		workingString=workingString.substring(1);
		int index=workingString.indexOf("/");
		if (index==-1) return workingString;
		if (index==0) return "";
		return workingString.substring(0,index);
	}
	
	protected static String getNextTrimmedPath(String workingString) throws MMLPathException { //returns null if no more items
		if (workingString.length()==0) return null;
		if (workingString.length()==1) {
			if (workingString.charAt(0)=='/') return null;
			else throw new MMLPathException("Bad path Exception 3");
		}
		
		workingString=workingString.substring(1); //chop off the first bit.
		int index=workingString.indexOf("/");
		if (index==-1) return null;
		if (index==0) {
			throw new DoubleSlashException("// has occured");
			//if (workingString.length()==1) return null;//"";
			//else return getNextTrimmedPath(workingString.substring(1, workingString.length()));
		}
		return workingString.substring(index,workingString.length());
	}
	
	protected static PathVector parsePath(String path) throws MMLPathException{
		if (path==null) throw new NullPointerException();
		if (path.length()<1) throw new MMLPathException("Path is too short: "+path);
		if (path.charAt(0)!='/') throw new NoStartingSlashException(path);
		if (path.indexOf("//")!=-1) throw new DoubleSlashException(path);
		
		PathVector vector=new PathVector(10,50);
		do {
			String activeToken=getNextName(path);
			vector.addElement(activeToken);
			path=getNextTrimmedPath(path);
		} while (path!=null);
		return vector;
	}
	
	private static class PathVector extends Vector{
		public PathVector(int i, int j) {
			super(i,j);
		}
		
		public boolean hasMore(int i) {
			return (i<size()-1);
		}
		
		public String getToken(int i) {
			return (String)(elementAt(i));
		}
		
		public boolean isLeafPath() {
			return !(getToken(size()-1).equals(""));
		}
		
		public boolean isBranchPath() {
			return (getToken(size()-1).equals(""));
		}
		
	}
	
	//Takes an MML string
	protected static Branch createBranch(String s, String mytag) throws MMLException {
		if (s==null) return null;//

		Branch branch=new Branch(mytag);
		
		for (int i=s.indexOf("<"); i!=-1; i=s.indexOf("<",i)) {
			String tag=s.substring(i+1, s.indexOf(">",i));
			
			int last2=lastBalenced(s, i);//=s.indexOf("</"+tag+">", i);
			int last=s.indexOf("</"+tag+">", last2);
			if (last!=last2) System.out.println("MML Error: "+last+" != "+last2 +" in "+s);

			Link mylink=new Link();
			//System.out.println(s.substring(s.indexOf(">",i)+1, last));
			mylink.value=createNode(s.substring(s.indexOf(">",i)+1, last), tag);
			Link.addLink(branch.head, mylink);
			
			i=last+1;
		}
		return branch;
	}
	
	private static int lastBalenced(String string, int startIndex) {
		int levelCount=0;
		for (int i=startIndex; i<string.length(); i++) {
			if (string.charAt(i)=='<') {
				if (string.charAt(i+1)=='/') {
					levelCount--;
				} else {
					levelCount++;
				}
			}
			
			if (levelCount<=0) return i;
		}
		
		return -1;
	}
	
	//Takes an MML String!
	private static Node createNode(String s, String tag) throws MMLException {

		if (s.indexOf("<")!=-1) {
			return createBranch(s, tag);
		} else {
			if (s.equals("")) return createBranch(s,tag); //wee
			else return createLeaf(s, tag);
		}
	}
	
	//takes an MML string
	private static Leaf createLeaf(String s, String tag) throws MMLException {
		Leaf leaf=new Leaf(tag, poluteString(s));
		return leaf;
	}
	
	//Returns the MML representation of the tree
	public String toString() {
		if (startNode==null) return "Null startNode error";
		return MML.makeString(startNode);
	}
	
	//Returns the MML representation of the node
	private static String makeString(Node node) {
		String temp="";
		if (node instanceof Branch) {
			Branch branch=(Branch)node;
			if (branch.tag!=null) temp=temp+"<"+branch.tag+">";
			for (Link iterator=branch.head; iterator.next!=null; iterator=iterator.next) {
				temp=temp+makeString(iterator.next.value);
			}
			if (branch.tag!=null) temp=temp+"</"+branch.tag+">";
		} else if (node instanceof Leaf) {
			Leaf leaf=(Leaf)node;
			if (leaf.tag!=null) temp=temp+"<"+leaf.tag+">";
			temp=temp+cleanString(leaf.value);
			if (leaf.tag!=null) temp=temp+"</"+leaf.tag+">";
		} else {
			System.out.println("Impossible");
		}
		return temp;
	}
	
	private abstract static class Node implements Serializable{
		public final String tag;
		
		public Node(String s)  throws InvalidTokenException {
			if (s==null) {
				tag=null; //should only happen once
			}else {
				if (s.indexOf("/")!=-1) throw new InvalidTokenException("/");
				else if (s.indexOf("<")!=-1) throw new InvalidTokenException("<");
				else if (s.indexOf(">")!=-1) throw new InvalidTokenException(">");
				tag=s;
			}
		}
		
		public boolean equals(Branch b) {
			return b.tag.equals(tag);
		}
	}
	
	private static class Branch extends Node implements Serializable {
		public final Link head;
		public Branch(String tag) throws InvalidTokenException {
			super(tag);
			head=new Link();
		}
	
	}
	
	
	private static class Leaf extends Node implements Serializable {
		public final String value;
		public Leaf(String tag, String value) throws InvalidTokenException,NullValueException {
			super(tag);
			if (value==null||value.equals("")) throw new NullValueException();
			this.value=value;
		}
	}
	
	private static class RootNode extends Branch implements Serializable {
		public RootNode() throws InvalidTokenException {
			super(null);
		}
	}
	
	private static class Link implements Serializable {
		public Node value;
		public Link next;
		
		public Link(Node v ) {value=v;}
		
		public Link(){}
		
		public static void addLink(Link head, Link link) {
			Link iterator;
			for (iterator=head; iterator.next!=null; iterator=iterator.next)
				;
			iterator.next=link;
		}
		
		public static Node getNode(Link head, String value) {
			for (Link iterator=head; iterator.next!=null; iterator=iterator.next) if (iterator.next.value.tag.equals(value)) return iterator.next.value;
			return null;
		}
		
		public static void removeLink(Link head, String value) {
			for (Link iterator=head; iterator.next!=null; iterator=iterator.next) {
				if (iterator.next.value.tag.equals(value)) {
					iterator.next=iterator.next.next;
					break; //important
				}
			}
		}
		
		public static void list(Link head, Vector collection) { //ha ha ha ha ho ho ho.. collection eh?
			for (Link iterator=head; iterator.next!=null; iterator=iterator.next) collection.addElement(iterator.next.value.tag);
		}
	}
	
	
		/*
	*	Puts an extra T after each occurence of LT
	*	Then takes all "<" and replaces them with LTS
	*   LTS == less Than String
	*/
	static final String BITSTUFF="LT";
	static final String KILLERSTRING=BITSTUFF+"S";
	static final String STUFF="T";
	
	protected static String cleanString(String s) {
		return replaceGreaterThans(bitStuff(s));
	}
	protected static String poluteString(String s) {
		return bitUnstuff(placeGreaterThans(s));
	}
	
	private static String bitUnstuff(String s) {
		if (s.indexOf(BITSTUFF)!=-1){
			String outstring="";
			int startindex=0;
			int endindex=0;
			
			while (startindex<s.length()) {
				endindex=s.indexOf(BITSTUFF, startindex);
				if (endindex<0) {
					outstring=outstring+s.substring(startindex, s.length());
					break;
				}
				outstring=outstring+s.substring(startindex, endindex+BITSTUFF.length())+"";
				startindex=endindex+BITSTUFF.length()+1;
			}
			return outstring;
		}
		return s;
	}
	
	private static String placeGreaterThans(String s) {
		if (s.indexOf(KILLERSTRING)!=-1){
			String outstring="";
			int startindex=0;
			int endindex=0;
			
			while (startindex<s.length()) {
				endindex=s.indexOf(KILLERSTRING, startindex);
				if (endindex<0) {
					outstring=outstring+s.substring(startindex, s.length());
					break;
				}
				outstring=outstring+s.substring(startindex, endindex)+"<";
				startindex=endindex+KILLERSTRING.length();
			}
			return outstring;
		}
		return s;
	}
	
	private static String replaceGreaterThans(String s) {
		if (s.indexOf("<")!=-1){
			String outstring="";
			int startindex=0;
			int endindex=0;
			
			while (startindex<s.length()) {
				endindex=s.indexOf("<", startindex);
				if (endindex<0) {
					outstring=outstring+s.substring(startindex, s.length());
					break;
				}
				outstring=outstring+s.substring(startindex, endindex)+KILLERSTRING;
				startindex=endindex+1;
			}
			return outstring;
		}
		return s;
	}
	
	private static String bitStuff(String s) {
		if (s.indexOf(BITSTUFF)!=-1) {
			String outstring="";
			int endindex=0;
			int startindex=0;
			
			while (startindex<s.length()) {
				endindex=s.indexOf(BITSTUFF, startindex);
				if (endindex<0) {
					outstring=outstring+s.substring(startindex, s.length());
					break;
				}
				outstring=outstring+s.substring(startindex,endindex)+BITSTUFF+STUFF;
				startindex=endindex+BITSTUFF.length();
			}
			return outstring;
		}
		return s;
	}

	
	public static void main(String args[]) {
		//MML moo=new MML();
		//moo.createMML();
		//System.out.println(""+moo);
		//System.out.println(""+moo.mml);
		//System.gc();
		//try {System.in.read();} catch (Exception ex){}
	}
}
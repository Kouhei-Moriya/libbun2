package org.libbun.peg4d;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.libbun.Functor;
import org.libbun.Main;
import org.libbun.UCharset;
import org.libbun.UList;

public abstract class ParserContext {
	public final          ParserSource source;
	protected long        sourcePosition = 0;
	public    long        endPosition;
	public    Grammar     rules = null;
	
	long statBacktrackCount = 0;
	long statBacktrackSize = 0;
	long statWorstBacktrack = 0;
	int  statObjectCount = 0;

	public ParserContext(ParserSource source, long startIndex, long endIndex) {
		this.source = source;
		this.sourcePosition = startIndex;
		this.endPosition = endIndex;
	}
	
	public abstract void setRuleSet(Grammar ruleSet);

	protected final long getPosition() {
		return this.sourcePosition;
	}
	protected final void setPosition(long pos) {
		this.sourcePosition = pos;
	}
	protected final void rollback(long pos) {
		long len = this.sourcePosition - pos;
		if(len > 0) {
			this.statBacktrackCount = this.statBacktrackCount + 1;
			this.statBacktrackSize = this.statBacktrackSize + len;
			if(len > this.statWorstBacktrack) {
				this.statWorstBacktrack = len;
			}
		}
		this.sourcePosition = pos;
	}
	@Override
	public final String toString() {
		if(this.endPosition > this.sourcePosition) {
			return this.source.substring(this.sourcePosition, this.endPosition);
		}
		return "";
	}
	public final boolean hasChar() {
		return this.sourcePosition < this.endPosition;
	}
	protected final char charAt(long pos) {
		if(pos < this.endPosition) {
			return this.source.charAt(pos);
		}
		return '\0';
	}

	protected final char getChar() {
		return this.charAt(this.sourcePosition);
	}
	
	public String substring(long startIndex, long endIndex) {
		if(endIndex <= this.endPosition) {
			return this.source.substring(startIndex, endIndex);
		}
		return "";
	}

	protected final void consume(long plus) {
		this.sourcePosition = this.sourcePosition + plus;
	}

	protected final boolean match(char ch) {
		if(ch == this.getChar()) {
			this.consume(1);
			return true;
		}
		return false;
	}

	protected final boolean match(String text) {
		if(this.endPosition - this.sourcePosition >= text.length()) {
			for(int i = 0; i < text.length(); i++) {
				if(text.charAt(i) != this.source.charAt(this.sourcePosition + i)) {
					return false;
				}
			}
			this.consume(text.length());
			return true;
		}
		return false;
	}

	protected final boolean match(UCharset charset) {
		if(charset.match(this.getChar())) {
			this.consume(1);
			return true;
		}
		return false;
	}
	
	protected final long matchZeroMore(UCharset charset) {
		for(;this.hasChar(); this.consume(1)) {
			char ch = this.source.charAt(this.sourcePosition);
			if(!charset.match(ch)) {
				break;
			}
		}
		return this.sourcePosition;
	}
	
	public final String formatErrorMessage(String msg1, String msg2) {
		return this.source.formatErrorMessage(msg1, this.sourcePosition, msg2);
	}

	public final void showPosition(String msg) {
		showPosition(msg, this.getPosition());
	}

	public final void showPosition(String msg, long pos) {
		System.out.println(this.source.formatErrorMessage("debug", pos, msg));
	}

	public final void showErrorMessage(String msg) {
		System.out.println(this.source.formatErrorMessage("error", this.sourcePosition, msg));
		Main._Exit(1, msg);
	}
	
	public boolean hasNode() {
		this.matchZeroMore(UCharset.WhiteSpaceNewLine);
		return this.sourcePosition < this.endPosition;
	}

	public PegObject parseNode(String startPoint) {
		this.initMemo();
		Peg start = this.getRule(startPoint);
		if(start == null) {
			Main._Exit(1, "undefined start rule: " + startPoint );
		}
		PegObject o = start.simpleMatch(new PegObject("#toplevel"), this);
		if(o.isFailure()) {
			o = this.newErrorObject();
			o.optionalToken = this.source.formatErrorMessage("syntax error", o.startIndex, "");
			System.out.println(o.optionalToken);
		}
		return o;
	}
	
	protected Memo memoMap = null;
	public abstract void initMemo();
	
	
	protected PegObject successResult = null;
	
	public final void setRecognitionOnly(boolean checkMode) {
		if(checkMode) {
			this.successResult = new PegObject("#success", this.source, null, 0);
		}
		else {
			this.successResult = null;
		}
	}
	
	public final boolean isRecognitionOnly() {
		return this.successResult != null;
	}
	
	public final PegObject newPegObject(String name, Peg created, long pos) {
		if(this.isRecognitionOnly()) {
			this.successResult.startIndex = pos;
			return this.successResult;
		}
		else {
			this.statObjectCount = this.statObjectCount + 1;
			PegObject node = new PegObject(name, this.source, created, pos);
			return node;
		}
	}
	
	protected final PegObject foundFailureNode = new PegObject(null, this.source, null, 0);

	public final PegObject newErrorObject() {
		PegObject node = newPegObject("#error", this.foundFailureNode.createdPeg, this.foundFailureNode.startIndex);
		node.matched = Functor.ErrorFunctor;
		return node;
	}
	
	public final PegObject foundFailure(Peg created) {
		if(this.sourcePosition >= this.foundFailureNode.startIndex) {  // adding error location
			this.foundFailureNode.startIndex = this.sourcePosition;
			this.foundFailureNode.createdPeg = created;
		}
		return this.foundFailureNode;
	}

	public final PegObject refoundFailure(Peg created, long pos) {
		this.foundFailureNode.startIndex = pos;
		this.foundFailureNode.createdPeg = created;
		return this.foundFailureNode;
	}

	public abstract Peg getRule(String symbol);

	public PegObject matchNonTerminal(PegObject left, PegNonTerminal e) {
		Peg next = this.getRule(e.symbol);
//		if(Main.VerboseStatCall) {
//			next.countCall(this, e.symbol, this.getPosition());
//		}
		return next.performMatch(left, this);
	}

	public final PegObject matchString(PegObject left, PegString e) {
		if(this.match(e.text)) {
			return left;
		}
		return this.foundFailure(e);
	}

	public final PegObject matchCharacter(PegObject left, PegCharacter e) {
		char ch = this.getChar();
		if(!e.charset.match(ch)) {
			return this.foundFailure(e);
		}
		this.consume(1);
		return left;
	}

	public final PegObject matchAny(PegObject left, PegAny e) {
		if(this.hasChar()) {
			this.consume(1);
			return left;
		}
		return this.foundFailure(e);
	}



	public PegObject matchOptional(PegObject left, PegOptional e) {
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		PegObject parsedNode = e.inner.performMatch(left, this);
		if(parsedNode.isFailure()) {
			this.popBack(markerId);
			this.rollback(pos);
			return left;
		}
		return parsedNode;
	}

	public PegObject matchRepeat(PegObject left, PegRepeat e) {
		PegObject prevNode = left;
		int count = 0;
		int markerId = this.pushNewMarker();
		while(this.hasChar()) {
			long pos = this.getPosition();
			markerId = this.pushNewMarker();
			PegObject node = e.inner.performMatch(prevNode, this);
			if(node.isFailure()) {
				assert(pos == this.getPosition());
				if(count < e.atleast) {
					this.popBack(markerId);
					return node;
				}
				break;
			}
			prevNode = node;
			//System.out.println("startPostion=" + startPosition + ", current=" + this.getPosition() + ", count = " + count);
			if(!(pos < this.getPosition())) {
				if(count < e.atleast) {
					return this.foundFailure(e);
				}
				break;
			}
			count = count + 1;
			if(!this.hasChar()) {
				markerId = this.pushNewMarker();
			}
		}
		this.popBack(markerId);
		return prevNode;
	}

	public PegObject matchAnd(PegObject left, PegAnd e) {
		PegObject node = left;
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		node = e.inner.performMatch(node, this);
		this.popBack(markerId);
		this.rollback(pos);
		return node;
	}

	public PegObject matchNot(PegObject left, PegNot e) {
		PegObject node = left;
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		node = e.inner.performMatch(node, this);
		this.popBack(markerId);
		this.rollback(pos);
		if(node.isFailure()) {
			return left;
		}
		return this.foundFailure(e);
	}

	public PegObject matchSequence(PegObject left, PegSequence e) {
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		for(int i = 0; i < e.size(); i++) {
			PegObject parsedNode = e.get(i).performMatch(left, this);
			if(parsedNode.isFailure()) {
				this.popBack(markerId);
				this.rollback(pos);
				return parsedNode;
			}
			left = parsedNode;
		}
		return left;
	}

	public PegObject matchChoice(PegObject left, PegChoice e) {
		PegObject node = left;
		long pos = this.getPosition();
		for(int i = 0; i < e.size(); i++) {
			int markerId = this.pushNewMarker();
			node = e.get(i).performMatch(left, this);
			if(!node.isFailure()) {
				break;
			}
			this.popBack(markerId);
			this.setPosition(pos);
		}
		return node;
	}
	
	private class ObjectLog {
		ObjectLog next;
		int  id;
		PegObject parentNode;
		int  index;
		PegObject childNode;
	}

	ObjectLog logStack = new ObjectLog();  // needs first logs
	ObjectLog unusedLog = null;
	int usedLog = 0;
	int maxLog  = 0;
	
	private ObjectLog newLog() {
		if(this.unusedLog == null) {
			maxLog = maxLog + 1;
			return new ObjectLog();
		}
		ObjectLog l = this.unusedLog;
		this.unusedLog = l.next;
		l.next = null;
		return l;
	}
	
	protected final int pushNewMarker() {
		return this.logStack.id;
	}

	protected final void popBack(int markerId) {
		ObjectLog cur = this.logStack;
		if(cur.id > markerId) {
			ObjectLog unused = this.logStack;
			while(cur != null) {
				//System.out.println("pop cur.id="+cur.id + ", marker="+markerId);
				if(cur.id == markerId + 1) {
					this.logStack = cur.next;
					cur.next = this.unusedLog;
					this.unusedLog = unused;
					break;
				}
				cur.parentNode = null;
				cur.childNode = null;
				cur = cur.next;
			}
		}
	}
	
	protected final void pushSetter(PegObject parentNode, int index, PegObject childNode) {
		if(!this.isRecognitionOnly()) {
			ObjectLog l = this.newLog();
			l.parentNode = parentNode;
			l.childNode  = childNode;
			l.index = index;
			l.id = this.logStack.id + 1;
			l.next = this.logStack;
			this.logStack = l;
			//System.out.println("push " + l.id + ", index= " + index);
		}
	}

	protected final void popNewObject(PegObject newnode, long startIndex, int marker) {
		if(!this.isRecognitionOnly()) {
			ObjectLog cur = this.logStack;
			if(cur.id > marker) {
				UList<ObjectLog> entryList = new UList<ObjectLog>(new ObjectLog[8]);
				ObjectLog unused = this.logStack;
				while(cur != null) {
					//System.out.println("object cur.id="+cur.id + ", marker="+marker);
					if(cur.parentNode == newnode) {
						entryList.add(cur);
					}
					if(cur.id == marker + 1) {
						this.logStack = cur.next; 
						cur.next = this.unusedLog;
						this.unusedLog = unused;
						break;
					}
					cur = cur.next;
				}
				if(entryList.size() > 0) {
					newnode.expandAstToSize(entryList.size());
					int index = 0;
					for(int i = entryList.size() - 1; i >= 0; i--) {
						ObjectLog l = entryList.ArrayValues[i];
						if(l.index == -1) {
							l.index = index;
						}
						index += 1;
					}
					for(int i = entryList.size() - 1; i >= 0; i--) {
						ObjectLog l = entryList.ArrayValues[i];
						newnode.set(l.index, l.childNode);
						l.childNode = null;
					}
					newnode.checkNullEntry();
				}
				entryList = null;
			}
			newnode.setSource(startIndex, this.getPosition());
		}
	}
	
	public PegObject matchNewObject(PegObject left, PegNewObject e) {
		PegObject leftNode = left;
		long startIndex = this.getPosition();
		if(Main.VerboseStatCall) {
			this.count(e, startIndex);
		}
		if(e.predictionIndex > 0) {
			for(int i = 0; i < e.predictionIndex; i++) {
				PegObject node = e.get(i).performMatch(left, this);
				if(node.isFailure()) {
					this.rollback(startIndex);
					return node;
				}
				assert(left == node);
			}
		}
		int markerId = this.pushNewMarker();
		PegObject newnode = this.newPegObject(e.nodeName, e, startIndex);
		if(e.leftJoin) {
			this.pushSetter(newnode, -1, leftNode);
		}
		for(int i = e.predictionIndex; i < e.size(); i++) {
			PegObject node = e.get(i).performMatch(newnode, this);
			if(node.isFailure()) {
				this.popBack(markerId);
				this.rollback(startIndex);
				return node;
			}
			//			if(node != newnode) {
			//				e.warning("dropping @" + newnode.name + " " + node);
			//			}
		}
		this.popNewObject(newnode, startIndex, markerId);
		return newnode;
	}
	
	long statExportCount = 0;
	long statExportSize  = 0;
	long statExportFailure  = 0;

	public PegObject matchExport(PegObject left, PegExport e) {
		PegObject pego = e.inner.simpleMatch(left, this);
		if(!pego.isFailure()) {
			this.statExportCount += 1;
			this.statExportSize += pego.length;
			this.pushBlockingQueue(pego);
		}
		else {
			this.statExportFailure += 1;
		}
		return left;
	}

	private BlockingQueue<PegObject> queue = null; 
	protected void pushBlockingQueue(PegObject pego) {
		if(this.queue != null) {
			try {
				this.queue.put(pego);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public PegObject matchSetter(PegObject left, PegSetter e) {
		long pos = left.startIndex;
		PegObject node = e.inner.performMatch(left, this);
		if(node.isFailure() || left == node) {
			return node;
		}
		if(this.isRecognitionOnly()) {
			left.startIndex = pos;
		}
		else {
			this.pushSetter(left, e.index, node);
		}
		return left;
	}

	public PegObject matchTag(PegObject left, PegTagging e) {
		left.tag = e.symbol;
		return left;
	}

	public PegObject matchMessage(PegObject left, PegMessage e) {
		left.optionalToken = e.symbol;
		left.startIndex = this.getPosition();
		return left;
	}
	
	public PegObject matchIndent(PegObject left, PegIndent e) {
		String indent = left.source.getIndentText(left.startIndex);
		//System.out.println("###" + indent + "###");
		if(this.match(indent)) {
			return left;
		}
		return this.foundFailure(e);
	}

	public PegObject matchIndex(PegObject left, PegIndex e) {
		String text = left.textAt(e.index, null);
		if(text != null) {
			if(this.match(text)) {
				return left;
			}
		}
		return this.foundFailure(e);
	}

//	public PegObject matchCatch(PegObject left, PegCatch e) {
//		e.inner.performMatch(left, this);
//		return left;
//	}



	
	
//	protected final long getpos(long keypos) {
//		return this.getpos1(keypos);
//	}
//
//	protected final void setMemo(long keypos, Peg keypeg, PegObject generated, int consumed) {
//		this.setMemo1(keypos, keypeg, generated, consumed);
//	}
//
//	protected final ObjectMemo getMemo(Peg keypeg, long keypos) {
//		return this.getMemo1(keypeg, keypos);
//	}
//	
	Map<Long, Peg> countMap = null;
	private UList<Peg> statCalledPegList = null;

	private int statCallCount = 0;
	private int statRepeatCount = 0;
	
	private void checkCountMap() {
		if(this.countMap == null) {
			this.statCallCount = 0;
			this.statRepeatCount = 0;
			this.countMap = new HashMap<Long, Peg>();
			this.statCalledPegList = new UList<Peg>(new Peg[256]);
		}
	}
	
	protected final void count(Peg e, long pos) {
		e.statCallCount += 1;
		statCallCount += 1;
		if(Main.VerboseStatCall) {
			checkCountMap();
			Long key = Memo.makekey(pos, e);
			Peg p = this.countMap.get(key);
			if(p != null) {
				assert(p == e);
				p.statRepeatCount += 1;
				statRepeatCount += 1;
			}
			else {
				this.countMap.put(key, e);
			}
			if(e.statCallCount == 1) {
				this.statCalledPegList.add(e);
			}
		}
	}
	
	
	long statErapsedTime = 0;
	long usedMemory;
	int statOptimizedPeg = 0;
	
		
	private String ratio(double num) {
		return String.format("%.3f", num);
	}
	private String Punit(String unit) {
		return "[" + unit +"]";
	}

	private String Kunit(long num) {
		return String.format("%.3f", (double)num / 1024);
	}
	
	private String Munit(double num) {
		return String.format("%.3f", (double)num/(1024*1024));
	}

	private String Nunit(long num, String unit) {
		return num + Punit(unit);
	}

	private String Kunit(long num, String unit) {
		return ratio((double)num / 1024) + Punit(unit);
	}
	
	private String Munit(long num, String unit) {
		return ratio((double)num/(1024*1024)) + Punit(unit);
	}

	private String KMunit(long num, String unit, String unit2) {
		return Kunit(num, unit) + " " + Munit(num, unit2);
	}

	private String kpx(double num) {
		return ratio(num / 1024);
	}

	private String kpx(double num, String unit) {
		return kpx(num) + Punit(unit);
	}

	private String mpx(double num) {
		return ratio(num / (1024*1024));
	}

	private String mpx(double num, String unit) {
		return mpx(num) + Punit(unit);
	}


	
	public void beginStatInfo() {
		System.gc(); // meaningless ?
		this.statBacktrackSize = 0;
		this.statBacktrackCount = 0;
		long total = Runtime.getRuntime().totalMemory();
		long free =  Runtime.getRuntime().freeMemory();
		usedMemory =  total - free;
		statErapsedTime = System.currentTimeMillis();
	}

	public void endStatInfo(PegObject parsedObject) {
		statErapsedTime = (System.currentTimeMillis() - statErapsedTime);
		System.gc(); // meaningless ?
		if(Main.VerboseStat) {
			System.gc(); // meaningless ?
			if(Main.VerbosePeg) {
				System.out.println("parsed:\n" + parsedObject);
				if(this.hasChar()) {
					System.out.println("** uncosumed: '" + this.source + "' **");
				}
			}
			long statCharLength = this.getPosition();
			long statFileLength = this.source.getFileLength();
			long statReadLength = this.source.statReadLength;
			double fileKps = (statFileLength) / (statErapsedTime / 1000.0);
			System.out.println("parser: " + this.getClass().getSimpleName() + " -O" + Main.OptimizedLevel + " -Xw" + Main.MemoFactor + " optimized peg: " + this.statOptimizedPeg );
			System.out.println("file: " + this.source.fileName + " filesize: " + KMunit(statFileLength, "Kb", "Mb"));
			System.out.println("IO: " + this.source.statIOCount +" read/file: " + ratio((double)statReadLength/statFileLength) + " pagesize: " + Nunit(FileSource.PageSize, "bytes") + " read: " + KMunit(statReadLength, "Kb", "Mb"));
			System.out.println("erapsed time: " + Nunit(statErapsedTime, "msec") + " speed: " + kpx(fileKps,"KiB/s") + " " + mpx(fileKps, "MiB/s"));
			System.out.println("backtrack raito: " + ratio((double)this.statBacktrackSize / statCharLength) + " backtrack: " + this.statBacktrackSize + " length: " + this.source.length() + ", consumed: " + statCharLength);
			System.out.println("backtrack_count: " + this.statBacktrackCount + " average: " + ratio((double)this.statBacktrackSize / this.statBacktrackCount) + " worst: " + this.statWorstBacktrack);
			int usedObject = parsedObject.count(); 
			System.out.println("object: created: " + this.statObjectCount + " used: " + usedObject + " disposal ratio u/c " + ratio((double)usedObject/this.statObjectCount) + " stacks: " + maxLog);
			System.out.println("stream: exported: " + this.statExportCount + ", size: " + this.statExportSize + " failure: " + this.statExportFailure);
			System.out.println("calls: " + this.statCallCount + " repeated: " + this.statRepeatCount + " r/c: " + ratio((double)this.statRepeatCount/this.statCallCount));
			System.out.println("memo hit: " + this.memoMap.memoHit + ", miss: " + this.memoMap.memoMiss + 
					", ratio: " + ratio(((double)this.memoMap.memoHit / (this.memoMap.memoMiss))) + ", consumed memo:" + this.memoMap.memoSize +" slots: " + this.memoMap.statMemoSlotCount);
			long total = Runtime.getRuntime().totalMemory();
			long free =  Runtime.getRuntime().freeMemory();
			long heap =  total - free;
			long used =  heap - usedMemory;
			System.out.println("heap: " + KMunit(heap, "KiB", "MiB") + " used: " + KMunit(used, "KiB", "MiB") + " heap/file: " + ratio((double) heap/ (statFileLength)));
			System.out.println();
		}
	}
	
//	private void showCallCounterList() {
//		if(this.statCallCounterList != null) {
//			for(int i = 0; i < this.statCallCounterList.size(); i++) {
//				CallCounter c = this.statCallCounterList.ArrayValues[i];
//				statCallCounter += c.total;
//				statCallRepeated += c.repeated;
//				if(Main.VerboseStat) {
//					System.out.println("\t"+c.ruleName+" calls: " + c.total + " repeated: " + c.repeated + " r/c: " + ratio((double)c.repeated/c.total));
//				}
//			}
//		}
//	}


	
}


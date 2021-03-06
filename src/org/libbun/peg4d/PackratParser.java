package org.libbun.peg4d;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.libbun.Main;
import org.libbun.UList;
import org.libbun.peg4d.Memo.ObjectMemo;

public class PackratParser extends RecursiveDecentParser {

	public PackratParser(ParserSource source) {
		super(source);
	}
	
	private PackratParser(ParserSource source, long startIndex, long endIndex, UList<Peg> pegList) {
		super(source, startIndex, endIndex, pegList);
	}

	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new PackratParser(source, startIndex, endIndex, this.pegList);
	}
	
	public void initMemo() {
		if(Main.MemoFactor == -1) {  /* default */
			this.memoMap = new PackratMemo(this.source.length());
		}
		else if(Main.MemoFactor == 0) {
			this.memoMap = new NoMemo();
		}
		else {
			this.memoMap = new FastFifoMemo(Main.MemoFactor);
		}

//		if(Main.MemoFactor == -1) {
//			this.memoMap = new HashMap<Long, ObjectMemo>();
//		}
//		else if(Main.MemoFactor == 0) {
//			this.statMemoSlotCount = FifoSize;
//			this.memoMap = new LinkedHashMap<Long, ObjectMemo>(this.statMemoSlotCount * 10) {  //FIFO
//				private static final long serialVersionUID = 6725894996600788028L;
//				long lastPosition = 0;
//				@Override
//				protected boolean removeEldestEntry(Map.Entry<Long, ObjectMemo> eldest)  {
//					if(this.size() > FifoSize) {
//						unusedMemo(eldest.getValue());
//						return true;			
//					}
//					return false;
//				}
//				@Override
//				public ObjectMemo put(Long key, ObjectMemo value) {
//					if(this.lastPosition < key) {
//						this.lastPosition = key;
//					}
//					return super.put(key, value);
//				}
//			};
//		}
//		else {
//			this.statMemoSlotCount = Main.MemoFactor;
//			this.memoMap = new LinkedHashMap<Long, ObjectMemo>(this.statMemoSlotCount * 10) {  //FIFO
//				private static final long serialVersionUID = 6725894996600788028L;
//				long lastPosition = 0;
//				@Override
//				protected boolean removeEldestEntry(Map.Entry<Long, ObjectMemo> eldest)  {
//					long diff = this.lastPosition - getpos(eldest.getKey());
//					if(statMemoSlotCount < this.size()) {
//						statMemoSlotCount = this.size();
//					}
//					if(diff > Main.MemoFactor) {
//						//System.out.println("removed pos="+eldest.getKey() + ", diff="+diff);
//						unusedMemo(eldest.getValue());
//						return true;			
//					}
//					return false;
//				}
//				@Override
//				public ObjectMemo put(Long key, ObjectMemo value) {
//					long pos = getpos(key);
//					if(this.lastPosition < pos) {
//						this.lastPosition = pos;
//					}
//					return super.put(key, value);
//				}
//			};
//		}
	}
	
	// Bugs existed -O0, but they disappeared !!
	public PegObject matchNonTerminal0(PegObject left, PegNonTerminal label) {
		Peg next = this.getRule(label.symbol);
		long pos = this.getPosition();
		ObjectMemo m = this.memoMap.getMemo(label, pos);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(label, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
//		if(Main.VerboseStatCall) {
//			next.countCall(this, label.symbol, pos);
//		}
		PegObject generated = next.performMatch(left, this);
		if(generated.isFailure()) {
			this.memoMap.setMemo(pos, label, null, (int)(generated.startIndex - pos));
		}
		else {
			this.memoMap.setMemo(pos, label, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}

	public PegObject matchNonTerminal(PegObject left, PegNonTerminal label) {
		Peg next = this.getRule(label.symbol);
		long pos = this.getPosition();
		ObjectMemo m = this.memoMap.getMemo(next, pos);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(label, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
//		if(Main.VerboseStatCall) {
//			next.countCall(this, label.symbol, pos);
//		}
		PegObject generated = next.performMatch(left, this);
		if(generated.isFailure()) {
			this.memoMap.setMemo(pos, next, null, (int)(generated.startIndex - pos));
		}
		else {
			this.memoMap.setMemo(pos, next, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}

}

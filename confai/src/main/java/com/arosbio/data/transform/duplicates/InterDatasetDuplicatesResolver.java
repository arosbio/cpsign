/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.duplicates;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;

/**
 * The {@link InterDatasetDuplicatesResolver} resolves duplicates between datasets and applies
 * a {@link DuplicatesResolverTransformer} implementation for how these should be handled. Note that
 * most {@link DuplicatesResolverTransformer} operates by keeping and altering the first occurrence of
 * a record and removes all the subsequent, so in most cases the first SubSet sent to {@link #transform(Dataset.SubSet, Dataset.SubSet)}
 * will retain the records with potentially altered label and it will be removed from the second SubSet.
 * @author staffan
 *
 */
public class InterDatasetDuplicatesResolver {
	
	private DuplicatesResolverTransformer transformer;
	
	public InterDatasetDuplicatesResolver(DuplicatesResolverTransformer transformStrategy) {
		this.transformer = transformStrategy;
	}
	
	public void transform(SubSet d1, SubSet d2) {
		SubSet dNew = new CustomSubSet(new MyBackedList<>(d1, d2));
		transformer.fitAndTransform(dNew);
	}
	
	@SuppressWarnings("serial")
	private class CustomSubSet extends SubSet {
		private List<DataRecord> recs;
		
		public CustomSubSet(List<DataRecord> recs) {
			this.recs = recs;
		}
		
		public Iterator<DataRecord> iterator(){
			return recs.iterator();
		}
		
		public DataRecord get(int index) {
			return recs.get(index);
		}
		
		public boolean add(DataRecord r) {
			return recs.add(r);
		}
		
		public int size() {
			return recs.size();
		}
		
		public DataRecord remove(int index) {
			return recs.remove(index);
		}
	}
	
	private class MyBackedList<T> implements List<T>{
		
		private List<T> l1;
		private List<T> l2;
		
		public MyBackedList(List<T> l1, List<T> l2) {
			this.l1 = l1;
			this.l2 = l2;
		}
		
		@Override
		public int size() {
			return l1.size()+l2.size();
		}

		@Override
		public boolean isEmpty() {
			return l1.isEmpty() && l2.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return l1.contains(o) || l2.contains(o);
		}

		@Override
		public Iterator<T> iterator() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Object[] toArray() {
			Object[] arr = new Object[l1.size()+l2.size()];
			for (int i=0;i<l1.size();i++) {
				arr[i] = l1.get(i);
			}
			int l1_size = l1.size();
			for(int i=0;i<l2.size();i++)
				arr[i+l1_size] = l2.get(i);
			return arr;
		}

		@Override
		public <T2> T2[] toArray(T2[] a) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean add(T e) {
			return l2.add(e); // Always add to the end
		}

		@Override
		public boolean remove(Object o) {
			boolean wasRm = l1.remove(o);
			if (!wasRm)
				return l2.remove(o);
			return wasRm;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			return l2.addAll(c);
		}

		@Override
		public boolean addAll(int index, Collection<? extends T> c) {
			if (index < l1.size()) {
				return l1.addAll(index,c);
			}
			// Else, we're in the next list
			return l2.addAll(index - l1.size(), c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			// Optional - so skip this
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			// Optional - so skip this
			return false;
		}

		@Override
		public void clear() {
			l1.clear();
			l2.clear();
		}

		@Override
		public T get(int index) {
			if (index < l1.size()) {
				return l1.get(index);
			}
			return l2.get(index - l1.size());
		}

		@Override
		public T set(int index, T element) {
			if (index < l1.size()) {
				return l1.set(index, element);
			}
			return l2.set(index-l1.size(), element);
		}

		@Override
		public void add(int index, T element) {
			if (index < l1.size()) {
				l1.add(index, element);
			} else {
				l2.add(index-l1.size(), element);
			}
			
		}

		@Override
		public T remove(int index) {
			if (index <l1.size())
				return l1.remove(index);
			else
				return l2.remove(index-l1.size());
		}

		@Override
		public int indexOf(Object o) {
			int ind = l1.indexOf(o);
			if (ind>=0)
				return ind;
			ind = l2.indexOf(o);
			if (ind>=0)
				return l1.size() + ind;
			return -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			int ind = l2.lastIndexOf(o);
			if (ind >=0)
				return l1.size() + ind;
			return l1.lastIndexOf(o);
		}

		@Override
		public ListIterator<T> listIterator() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public ListIterator<T> listIterator(int index) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public List<T> subList(int fromIndex, int toIndex) {
			throw new RuntimeException("Not implemented");
		}
		
	}

}

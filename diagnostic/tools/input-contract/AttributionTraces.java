package com.vanipy.menit.diag;
import java.util.*;
import static com.vanipy.menit.diag.Reconciler.*;
/** Host-only fake labels and times; exercises the real unchanged Reconciler. */
public final class AttributionTraces {
 static final Set<String> W=new HashSet<>(Arrays.asList("synthetic.messenger"));
 static int cases=0;
 static Obs al(long t,int id){return new Obs(t,"synthetic.messenger",Label.ALLOWLISTED,id);}
 static Obs no(long t){return new Obs(t,"synthetic.messenger",Label.NOT_ALLOWLISTED,-1);}
 static Obs un(long t){return new Obs(t,"synthetic.messenger",Label.UNRECOGNISED,-1);}
 static Fg fg(long s,long e){return new Fg("synthetic.messenger",s,e);}
 static List<Cov> cv(long s,long e){return Arrays.asList(new Cov(s,e));}
 static void trace(String name,List<Fg> f,List<Obs> o,List<Cov> c,long... want){
  List<Seg> result=reconcile(W,f,o,c,Options.defaults());long[] got=new long[Cat.values().length];
  for(Seg s:result){if(s.e<=s.s)throw new AssertionError("empty segment");got[s.cat.ordinal()]+=s.e-s.s;}
  if(!Arrays.equals(got,want))throw new AssertionError(name+" got="+Arrays.toString(got)+" want="+Arrays.toString(want));
  cases++;System.out.print(name);for(long n:got)System.out.print(","+n);System.out.println();
 }
 public static void main(String[] args){
  System.out.println("case,RECOGNISED,PROBABLE,WATCHED_OTHER,UNKNOWN_FG,NO_DATA,PENDING,MULTI_WINDOW");
  trace("known_then_known_other",Arrays.asList(fg(0,60000)),Arrays.asList(al(0,1),no(20000)),cv(0,60000),20000,0,30000,10000,0,0,0);
  trace("no_header_never_credit",Arrays.asList(fg(0,60000)),Collections.emptyList(),cv(0,60000),0,0,0,60000,0,0,0);
  trace("explicit_unknown_ends_known",Arrays.asList(fg(0,60000)),Arrays.asList(al(0,1),un(10000)),cv(0,60000),10000,0,0,50000,0,0,0);
  trace("stale_30sec_not_entire_session",Arrays.asList(fg(0,60000)),Arrays.asList(al(0,1)),cv(0,60000),30000,0,0,30000,0,0,0);
  trace("service_gap_not_bridged",Arrays.asList(fg(0,60000)),Arrays.asList(al(0,1)),Arrays.asList(new Cov(0,10000),new Cov(20000,60000)),10000,0,0,40000,10000,0,0);
  trace("conflicting_known_ids_unknown",Arrays.asList(fg(0,30000)),Arrays.asList(al(0,1),al(0,2)),cv(0,30000),0,0,0,30000,0,0,0);
  trace("old_header_not_reused_after_exit",Arrays.asList(fg(0,10000),new Fg("synthetic.launcher",10000,20000),fg(20000,40000)),Arrays.asList(al(0,1)),cv(0,40000),10000,0,0,20000,0,0,0);
  trace("other_package_label_cannot_credit",Arrays.asList(fg(0,30000)),Arrays.asList(new Obs(0,"synthetic.other",Label.ALLOWLISTED,1)),cv(0,30000),0,0,0,30000,0,0,0);
  trace("observation_at_end_is_outside",Arrays.asList(fg(0,30000)),Arrays.asList(al(30000,1)),cv(0,30000),0,0,0,30000,0,0,0);
  trace("unsettled_process_tail_pending",Arrays.asList(new Fg("synthetic.messenger",0,30000,false)),Arrays.asList(al(0,1)),cv(0,30000),0,0,0,0,0,30000,0);
  trace("overlap_not_double_credit",Arrays.asList(fg(0,30000),new Fg("synthetic.other",10000,20000)),Arrays.asList(al(0,1)),cv(0,30000),10000,0,0,10000,0,0,10000);
  trace("probable_off_even_same_id_brackets",Arrays.asList(fg(0,100000)),Arrays.asList(al(0,1),al(90000,1)),cv(0,100000),40000,0,0,60000,0,0,0);
  System.err.println("PASS: "+cases+" attribution traces; real reconciler unchanged; synthetic labels/times only.");
 }
}

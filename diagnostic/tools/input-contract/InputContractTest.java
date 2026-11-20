package com.vanipy.menit.diag;
import java.util.*;
import static com.vanipy.menit.diag.Reconciler.*;
import static com.vanipy.menit.diag.SyntheticInputContract.*;

/** Fake package/label/time streams only. No Android/account/network objects. */
public final class InputContractTest {
 static final String P="synthetic.messenger";
 static final Set<String> W=new HashSet<>(Arrays.asList(P));
 static int n;
 static Event e(long t,Kind k){return new Event(7,t,k,P,null,-1);}
 static Event label(long t){return new Event(7,t,Kind.LABEL,P,Label.ALLOWLISTED,1);}
 static List<Event> base(){return new ArrayList<>(Arrays.asList(e(0,Kind.RESUME),e(0,Kind.HEARTBEAT),label(0),e(10000,Kind.HEARTBEAT),e(20000,Kind.HEARTBEAT),e(30000,Kind.HEARTBEAT),e(30000,Kind.PAUSE)));}
 static Result adapt(List<Event> events){return SyntheticInputContract.adapt(7,0,40000,10000,events);}
 static List<Seg> reconcile(Result r){return Reconciler.reconcile(W,r.foreground,r.observations,r.coverage,Options.defaults());}
 static long total(List<Seg> s,Cat c){return Reconciler.ms(s,c,null);}
 static void check(String name,boolean pass){n++;if(!pass)throw new AssertionError(name);System.out.println("PASS "+name);}
 static String canon(Result r){List<String> a=new ArrayList<>();for(Seg s:reconcile(r))a.add(s.cat+"|"+s.pkg+"|"+s.id+"|"+s.s+"|"+s.e);Collections.sort(a);return a.toString();}
 public static void main(String[] args){
  Result r=adapt(base());List<Seg>s=reconcile(r);
  check("clean resume/pause and bounded heartbeats credit 30s",total(s,Cat.RECOGNISED)==30000);
  List<Event>x=base();x.removeIf(a->a.kind==Kind.LABEL);s=reconcile(adapt(x));check("no header stays unknown",total(s,Cat.UNKNOWN_FG)==30000 && total(s,Cat.RECOGNISED)==0);
  x=base();x.removeIf(a->a.kind==Kind.HEARTBEAT);s=reconcile(adapt(x));check("no heartbeat means no reader coverage",total(s,Cat.NO_DATA)==30000);
  x=base();x.removeIf(a->a.kind==Kind.HEARTBEAT && a.t==10000);s=reconcile(adapt(x));check("20s gap is not bridged",total(s,Cat.NO_DATA)==20000 && total(s,Cat.UNKNOWN_FG)==10000 && total(s,Cat.RECOGNISED)==0);
  x=base();x.removeIf(a->a.kind==Kind.PAUSE);s=reconcile(adapt(x));check("missing close leaves whole open tail pending",total(s,Cat.PENDING)==40000 && total(s,Cat.RECOGNISED)==0);
  for(Kind stop:Arrays.asList(Kind.SCREEN_OFF,Kind.SHUTDOWN)) {
   x=base();x.removeIf(a->a.kind==Kind.PAUSE);x.add(e(25000,stop));s=reconcile(adapt(x));
   check(stop+" bounds foreground and coverage",total(s,Cat.RECOGNISED)==20000 && total(s,Cat.NO_DATA)==5000);
  }
  x=base();x.addAll(base());s=reconcile(adapt(x));check("duplicate events never double credit",total(s,Cat.RECOGNISED)==30000);
  x=base();x.removeIf(a->a.kind==Kind.LABEL);x.add(new Event(6,0,Kind.LABEL,P,Label.ALLOWLISTED,1));r=adapt(x);s=reconcile(r);check("previous boot label ignored",r.ignored==1 && total(s,Cat.UNKNOWN_FG)==30000);
  x=Arrays.asList(new Event(6,0,Kind.RESUME,P,null,-1),e(0,Kind.HEARTBEAT),label(0),e(10000,Kind.HEARTBEAT),e(10000,Kind.PAUSE));r=adapt(x);check("prior boot resume cannot create new epoch interval",r.foreground.isEmpty());
  x=base();x.add(new Event(7,0,Kind.RESUME,"synthetic.other",null,-1));r=adapt(x);check("simultaneous conflicting resumes fabricate no foreground",r.foreground.isEmpty());
  x=base();x.add(e(0,Kind.PAUSE));r=adapt(x);check("resume/pause tie is not silently ordered",r.foreground.isEmpty());
  x=base();x.add(new Event(7,30000,Kind.LABEL,P,Label.ALLOWLISTED,2));s=reconcile(adapt(x));check("boundary label at end earns no future credit",total(s,Cat.RECOGNISED)==30000);
  x=Arrays.asList(e(0,Kind.HEARTBEAT),label(0),e(10000,Kind.RESUME),e(10000,Kind.HEARTBEAT),e(20000,Kind.HEARTBEAT),e(20000,Kind.PAUSE));s=reconcile(adapt(x));check("label before foreground start not carried in",total(s,Cat.UNKNOWN_FG)==10000);
  x=base();x.add(e(15000,Kind.PAUSE));x.add(e(20000,Kind.RESUME));s=reconcile(adapt(x));check("reentry does not reuse prior header",total(s,Cat.RECOGNISED)==15000 && total(s,Cat.UNKNOWN_FG)==10000);
  x=base();x.add(e(45000,Kind.RESUME));r=adapt(x);check("out of window event rejected",r.ignored==1);
  boolean bad=false;try{SyntheticInputContract.adapt(7,0,100,0,base());}catch(IllegalArgumentException ex){bad=true;}check("nonpositive heartbeat gap rejected",bad);
  Random random=new Random(20);boolean deterministic=true;
  for(int i=0;i<1000;i++){x=base();Collections.shuffle(x,random);if(!canon(adapt(x)).equals(canon(adapt(base()))))deterministic=false;}
  check("1000 shuffled input orders agree",deterministic);
  System.out.println("ALL PASS "+n+" input-contract checks; synthetic host-only, not Android accuracy proof");
 }
}

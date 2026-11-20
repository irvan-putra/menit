package com.vanipy.menit.diag;

import java.util.*;
import static com.vanipy.menit.diag.Reconciler.*;

/** Test-only input contract. Never imported by Android code. All inputs are fake elapsed-time events. */
final class SyntheticInputContract {
    enum Kind { RESUME, PAUSE, SCREEN_OFF, SHUTDOWN, HEARTBEAT, LABEL }
    static final class Event {
        final int boot; final long t; final Kind kind; final String pkg; final Label label; final int id;
        Event(int boot,long t,Kind kind,String pkg,Label label,int id) {
            this.boot=boot; this.t=t; this.kind=kind; this.pkg=pkg; this.label=label; this.id=id;
        }
    }
    static final class Result {
        final List<Fg> foreground = new ArrayList<>();
        final List<Obs> observations = new ArrayList<>();
        final List<Cov> coverage = new ArrayList<>();
        int ignored;
    }
    /** One explicit boot epoch per call. No wall-clock conversion or current Android APIs. */
    static Result adapt(int boot,long start,long end,long heartbeatGap,List<Event> input) {
        if(start<0 || end<start || heartbeatGap<=0) throw new IllegalArgumentException("invalid window");
        Result r=new Result(); TreeMap<Long,List<Event>> groups=new TreeMap<>();
        for(Event e:input) {
            if(e.boot!=boot || e.t<start || e.t>end) { r.ignored++; continue; }
            groups.computeIfAbsent(e.t,k->new ArrayList<>()).add(e);
        }
        String active=null; long since=0; Long previousBeat=null;
        for(Map.Entry<Long,List<Event>> group:groups.entrySet()) {
            long t=group.getKey(); Set<String> resumes=new TreeSet<>(), pauses=new TreeSet<>();
            boolean stop=false,beat=false;
            for(Event e:group.getValue()) {
                switch(e.kind) {
                    case RESUME: if(e.pkg==null || e.pkg.isEmpty()) throw new IllegalArgumentException("resume package"); resumes.add(e.pkg); break;
                    case PAUSE: if(e.pkg==null || e.pkg.isEmpty()) throw new IllegalArgumentException("pause package"); pauses.add(e.pkg); break;
                    case SCREEN_OFF: case SHUTDOWN: stop=true; break;
                    case HEARTBEAT: beat=true; break;
                    case LABEL:
                        if(e.pkg==null || e.label==null) throw new IllegalArgumentException("label fields");
                        r.observations.add(new Obs(t,e.pkg,e.label,e.id)); break;
                }
            }
            // Coverage requires two actual fake heartbeats. Never extend a final beat into the future.
            if(beat) {
                if(previousBeat!=null && t>previousBeat && t-previousBeat<=heartbeatGap) r.coverage.add(new Cov(previousBeat,t));
                previousBeat=t;
            }
            boolean conflict=resumes.size()>1 || (!resumes.isEmpty() && (stop || pauses.contains(resumes.iterator().next())));
            if(stop || conflict) {
                if(active!=null && t>since) r.foreground.add(new Fg(active,since,t,!conflict));
                active=null;
                if(stop) previousBeat=null;
                // Conflicting simultaneous resume events supply no defensible package. No invented foreground.
                continue;
            }
            if(!resumes.isEmpty()) {
                String next=resumes.iterator().next();
                if(!next.equals(active)) {
                    if(active!=null && t>since) r.foreground.add(new Fg(active,since,t,true));
                    active=next; since=t;
                }
            } else if(active!=null && pauses.contains(active)) {
                if(t>since) r.foreground.add(new Fg(active,since,t,true));
                active=null;
            }
        }
        // Query end is not evidence of a clean close. Open tails remain PENDING in Reconciler.
        if(active!=null && end>since) r.foreground.add(new Fg(active,since,end,false));
        return r;
    }
}

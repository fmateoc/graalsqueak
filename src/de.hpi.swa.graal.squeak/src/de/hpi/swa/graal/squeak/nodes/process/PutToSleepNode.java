package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithImage;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

public abstract class PutToSleepNode extends AbstractNodeWithImage {
    @Child private LinkProcessToListNode linkProcessToList = LinkProcessToListNode.create();

    protected PutToSleepNode(final SqueakImageContext image) {
        super(image);
    }

    public static PutToSleepNode create(final SqueakImageContext image) {
        return PutToSleepNodeGen.create(image);
    }

    public abstract void executePutToSleep(PointersObject process);

    @Specialization
    protected final void doPutToSleep(final PointersObject process,
                    @CachedLibrary(limit = "1") final SqueakObjectLibrary objectLibrary) {
        // Save the given process on the scheduler process list for its priority.
        final int priority = (int) (long) process.at0(PROCESS.PRIORITY);
        final ArrayObject processLists = (ArrayObject) image.getScheduler().at0(PROCESS_SCHEDULER.PROCESS_LISTS);
        final PointersObject processList = (PointersObject) objectLibrary.at0(processLists, priority - 1);
        linkProcessToList.executeLink(process, processList);
    }
}

package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.model.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;

public final class LinkProcessToListNode extends AbstractNode {
    @Child private AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();
    @Child private AbstractPointersObjectWriteNode writeListNode = AbstractPointersObjectWriteNode.create();
    @Child private AbstractPointersObjectWriteNode writeProcessNode = AbstractPointersObjectWriteNode.create();

    private ConditionProfile isEmptyListProfile = ConditionProfile.createBinaryProfile();

    public static LinkProcessToListNode create() {
        return new LinkProcessToListNode();
    }

    public void executeLink(final PointersObject process, final PointersObject list) {
        if (isEmptyListProfile.profile(list.isEmptyList(readNode))) {
            // Add the given process to the given linked list and set the backpointer
            // of process to its new list.
            writeListNode.executeWrite(list, LINKED_LIST.FIRST_LINK, process);
            writeListNode.executeWrite(list, LINKED_LIST.LAST_LINK, process);
            writeProcessNode.executeWrite(process, PROCESS.LIST, list);
        } else {
            writeListNode.executeWrite((PointersObject) readNode.executeRead(list, LINKED_LIST.LAST_LINK), PROCESS.NEXT_LINK, process);
            writeListNode.executeWrite(list, LINKED_LIST.LAST_LINK, process);
            writeProcessNode.executeWrite(process, PROCESS.LIST, list);
        }
    }
}

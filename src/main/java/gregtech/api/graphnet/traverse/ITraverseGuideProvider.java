package gregtech.api.graphnet.traverse;

import gregtech.api.graphnet.NetNode;
import gregtech.api.graphnet.path.INetPath;
import gregtech.api.graphnet.predicate.test.IPredicateTestObject;

import org.jetbrains.annotations.Nullable;

public interface ITraverseGuideProvider<N extends NetNode, P extends INetPath<N, ?>, T extends IPredicateTestObject> {

    @Nullable
    <D extends ITraverseData<N, P>> TraverseGuide<N, P, D> getGuide(
                                                                    TraverseDataProvider<D, T> provider, T testObject,
                                                                    long flow, boolean simulate);
}

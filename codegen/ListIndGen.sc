val ListAIndGen = ConstructorSeqTL(ConstructorSeqDom.Cons(HoTT.Name("nil"), ConstructorShape.IdShape.byTyp("List(A)" :: Type), ConstructorSeqDom.Cons(HoTT.Name("cons"), ConstructorShape.CnstFuncConsShape("A" :: Type, ConstructorShape.FuncConsShape(IterFuncShape.IdIterShape.byTyp("List(A)" :: Type), ConstructorShape.IdShape.byTyp("List(A)" :: Type))), ConstructorSeqDom.Empty.byTyp("List(A)" :: Type))), "List(A)" :: Type)
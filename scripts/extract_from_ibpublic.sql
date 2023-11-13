-- Extract image metadata from ibpublic
-- ibpublic is the legacy database created by Jürgen Dönitz

WITH Image AS (
	SELECT ImageHolder.ID		AS id
		, ImageHolder.fsName	AS filename
		, CoreData.dsRNAID		AS watermark
	FROM ImageHolder

	INNER JOIN Feature
	ON Feature.ID = ImageHolder.feature_ID

	INNER JOIN CoreData
	ON CoreData.ID = Feature.coreData
)

SELECT id
	, filename
	, watermark
FROM Image
;
